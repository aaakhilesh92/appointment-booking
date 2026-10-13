package com.org.booking.service;

import com.org.booking.config.SharedConfig;
import com.org.booking.config.VfsConfig;
import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

import static com.org.booking.config.Constants.VFS;

@Service
public class VfsAppointmentService {
    private static final Logger log = LoggerFactory.getLogger(VfsAppointmentService.class);

    @Autowired
    private VfsConfig vfsConfig;

    @Autowired
    private SharedConfig sharedConfig;

    @Autowired
    private CredentialCooldownStore cooldownStore;

    @Autowired
    private VfsLoginAttemptRepository loginAttemptRepository;

    @Autowired
    private VfsSlotAlertRepository slotAlertRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private TelegramService telegramService;

    @Autowired
    private MediaPlayerService playerService;

    public void checkVfsAppointment() {
        List<Credential> credentials = Arrays.asList(
                new Credential(vfsConfig.getUsername1(), vfsConfig.getPassword1()),
                new Credential(vfsConfig.getUsername2(), vfsConfig.getPassword2())
        );

        boolean authenticated = false;

        // ------------------ Credential Rotation ------------------
        for (Credential cred : credentials) {
            String id = cred.username();
            if (cooldownStore.isDisabled(id)) {
                log.warn("Skipping {} — disabled until cooldown expires.", id);
                continue;
            }

            log.info("Trying login with {}", cred.username());

            LoginOutcome outcome = attemptFullLoginFlow(cred);
            if (outcome == LoginOutcome.AUTHENTICATED) {
                cooldownStore.recordSuccess(id);
                log.info("Login successful for {}. Consecutive failure count reset. Time: {}", id, LocalDateTime.now());
                authenticated = true;
                //break;
            }
            if (outcome == LoginOutcome.REJECTED) {
                CredentialCooldownStore.FailureStatus status = cooldownStore.recordFailure(id);
                if (status.isDisabled()) {
                    log.error("Login rejected for {}. Time: {}, {} consecutive failures; disabled until {}.",
                            id, LocalDateTime.now(), status.consecutiveFailures(), status.disabledUntil());
                } else {
                    log.warn("Login rejected for {}. Time: {}, Consecutive failures: {}.",
                            id, LocalDateTime.now(), status.consecutiveFailures());
                }
            } else {
                log.error("Could not determine login result for {}. Time: {}, failure count was not changed.", id, LocalDateTime.now());
            }
        }

        if (!authenticated) {
            log.error("No credential authenticated. All configured credentials were unavailable or unsuccessful.");
        }
    }

    // Helper method for human-like typing
    private void typeHumanLike(Locator locator, String text) {
        Random random = new Random();
        locator.click();
        for (char c : text.toCharArray()) {
            locator.pressSequentially(String.valueOf(c));
            // Random delay between 100ms to 250ms per character
            try {
                Thread.sleep(100 + random.nextInt(150));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ------------------ Full Login Flow ------------------
    private LoginOutcome attemptFullLoginFlow(Credential cred) {
        try (Playwright playwright = Playwright.create()) {

            BrowserContext context = launchBrowser(playwright);
            Page page = getInitialPage(context);

            handleCookies(page);
            navigateToLogin(page);

            Page loginPage = page.waitForPopup(() -> page.click(vfsConfig.getPopupLinkSelector()));
            loginPage.waitForLoadState();
            loginPage.waitForTimeout(3000);

            LoginOutcome outcome = attemptLogin(loginPage, cred);
            if (outcome != LoginOutcome.AUTHENTICATED) {
                return outcome;
            }

            try {
                proceedAfterLogin(loginPage);
                checkSlotAvailability(loginPage, cred.username());
            } catch (Exception e) {
                log.error("Login succeeded for {}, but the appointment flow failed, Message: {}", cred.username(), e.getMessage());
            }
            return LoginOutcome.AUTHENTICATED;
        } catch (Exception e) {
            log.error("Exception during login flow for {}, Message: {}", cred.username(), e.getMessage());
            return LoginOutcome.ERROR;
        }
    }

    // ------------------ Login Attempt ------------------
    private LoginOutcome attemptLogin(Page loginPage, Credential cred) {
        try {
            typeHumanLike(loginPage.locator(vfsConfig.getLoginEmailSelector()), cred.username());
            loginPage.waitForTimeout(1000);

            typeHumanLike(loginPage.locator(vfsConfig.getLoginPasswordSelector()), cred.password());
            loginPage.waitForTimeout(3000);

            loginPage.click(vfsConfig.getLoginSubmitSelector());
            Long attemptId = loginAttemptRepository.insert(cred.username(), false);
            loginPage.waitForTimeout(3000);

            if (loginPage.locator(vfsConfig.getLoginErrorSelector()).isVisible()) {
                log.warn("Login error: {}", loginPage.locator(vfsConfig.getLoginErrorSelector()).textContent());
                return LoginOutcome.REJECTED;
            }

            if (loginPage.url().contains(vfsConfig.getLoginUrlFragment())) {
                return LoginOutcome.REJECTED;
            }

            loginAttemptRepository.update(attemptId, true);
            return LoginOutcome.AUTHENTICATED;

        } catch (Exception e) {
            log.error("Login attempt failed, Message: {}", e.getMessage());
            return LoginOutcome.ERROR;
        }
    }

    private enum LoginOutcome {
        AUTHENTICATED,
        REJECTED,
        ERROR
    }

    private BrowserContext launchBrowser(Playwright playwright) {
        BrowserType.LaunchPersistentContextOptions options =
                new BrowserType.LaunchPersistentContextOptions()
                        .setHeadless(false)
                        .setViewportSize(null)
                        .setArgs(Arrays.asList(vfsConfig.getBrowserArgs()));

        return playwright.chromium().launchPersistentContext(Paths.get(vfsConfig.getBrowserDataPath()), options);
    }

    private Page getInitialPage(BrowserContext context) {
        Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
        page.navigate(vfsConfig.getUrl());
        page.waitForTimeout(4000);
        return page;
    }

    private void handleCookies(Page page) {
        try {
            Locator cookieBtn = page.locator(vfsConfig.getCookieAcceptSelector());
            if (cookieBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(4000))) {
                cookieBtn.click();
                log.info("Cookies accepted.");
            }
        } catch (Exception e) {
            log.info("Cookie banner skipped, Message: {}", e.getMessage());
        }
    }

    private void navigateToLogin(Page page) {
        log.info("Clicking Book Now...");
        page.click(vfsConfig.getBookNowSelector());
        page.waitForTimeout(2000);
    }

    // ------------------ After Login ------------------
    private void proceedAfterLogin(Page loginPage) {
        log.info("Clicking Start New Booking...");
        loginPage.locator(vfsConfig.getStartBookingSelector()).click();
        loginPage.waitForTimeout(2000);

        loginPage.locator(vfsConfig.getSubvisaCategorySelector()).click();
        loginPage.waitForTimeout(2000);
        loginPage.locator(vfsConfig.getLongStayOptionSelector()).click();
        loginPage.waitForTimeout(2000);

        loginPage.locator(vfsConfig.getSubCategorySelector()).click();
        loginPage.waitForTimeout(2000);
    }

    // ------------------ Slot Check ------------------
    private void checkSlotAvailability(Page loginPage, String email) {
        loginPage.onResponse(response -> {
            if (response.url().contains(vfsConfig.getSlotCheckResponseFragment())) {
                log.info("Intercepted slot check response: {}", response.text());
            }
        });

        loginPage.locator(vfsConfig.getResidencePermitOptionSelector()).click();
        loginPage.waitForTimeout(2000);

        String alertMessage = loginPage.locator(vfsConfig.getAlertSelector()).textContent().trim();
        log.info("Alert Message: {}", alertMessage);
        slotAlertRepository.insert(email, alertMessage);

        if (alertMessage.contains(vfsConfig.getNoSlotsMessage())) {
            handleSlotNotFound();
        } else {
            handleSlotFound();
        }
    }

    private void handleSlotFound() {
        log.info("!!! New VFS slots are available !!! Check the website immediately.");
        emailService.sendEmail(VFS);
        telegramService.sendTelegramNotification(VFS);
        playerService.playSound(sharedConfig.getRingtoneSiren());
    }

    private void handleSlotNotFound() {
        log.info("No VFS Slots are available at the moment.");
        telegramService.sendTelegramNoSlotNotification(VFS, null);
        // playerService.playSound(sharedConfig.getRingtoneNotification());
    }
}