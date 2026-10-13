package com.org.booking.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import com.org.booking.config.EmbassyConfig;
import com.org.booking.config.SharedConfig;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.org.booking.config.Constants.*;

@Service
public class AppointmentService {
    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final AtomicLong lastNoSlotNotificationTime = new AtomicLong();

    @Autowired
    private EmailService emailService;

    @Autowired
    private EmbassyConfig embassyConfig;

    @Autowired
    private SharedConfig sharedConfig;

    @Autowired
    private TelegramService telegramService;

    @Autowired
    private MediaPlayerService playerService;

    public void bookEmbassyAppointment() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = initializeBrowser(playwright);
            BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(null));
            Page page = context.newPage();
            page.setDefaultTimeout(Duration.ofSeconds(WAIT_TIMEOUT).toMillis());
            // Complete Application Step 1 and Step 2
            fillApplicationFormStep1AndStep2(page);
            // Complete Final Step and submit the form
            fillApplicationFormFinalStep(page);
            context.close();
            browser.close();
        } catch (Exception e) {
            log.error("An Exception occurred during bookEmbassyAppointment, Message: {}", e.getMessage());
        }
    }

    public void checkEmbassyAppointment() {
        String cookie = loadCookieFromFile();

        if (cookie == null || cookie.isBlank()) {
            log.info("No saved cookie found, creating new session");
            cookie = retrieveAndSaveNewCookie();
        } else {
            log.info("Using saved cookie from file");
        }

        String status = checkSlots(cookie == null || cookie.isBlank()
                ? List.of()
                : buildAvailableDatesRequest(cookie));
        if (STATUS_FOUND.equals(status)) {
            handleSlotFound();
        } else if (STATUS_NOT_FOUND.equals(status)) {
            handleSlotNotFound(cookie);
        } else {
            handleInvalidCookie();
        }
    }

    private void handleSlotFound() {
        log.info("!!! EMBASSY SLOTS FOUND !!! Check the website immediately.");
        emailService.sendEmail(EMBASSY);
        telegramService.sendTelegramNotification(EMBASSY);
        playerService.playSound(sharedConfig.getRingtoneSiren());
    }


    private synchronized void handleSlotNotFound(String cookie) {
        long now = System.currentTimeMillis();
        long lastNotification = lastNoSlotNotificationTime.get();
        long notificationInterval = sharedConfig.getNoSlotNotificationIntervalMs();
        if (now - lastNotification < notificationInterval) {
            log.info("Skipping Embassy no-slot notification; next notification is allowed in {} seconds.",
                    (notificationInterval - (now - lastNotification)) / 1000);
            return;
        }

        String availableDates = fetchAvailableDates(cookie);
        if (telegramService.sendTelegramNoSlotNotification(EMBASSY, availableDates)) {
            lastNoSlotNotificationTime.set(System.currentTimeMillis());
        } else {
            log.warn("Embassy no-slot notification failed; it will be retried on the next check.");
        }
        //playerService.playSound(sharedConfig.getRingtoneNotification());
    }

    private String fetchAvailableDates(String cookie) {
        try {
            HttpResponse<String> response = client.send(
                    buildDateApiRequest(cookie),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null) {
                log.warn("Available-dates request failed with status {}", response.statusCode());
                return NO_DATE;
            }
            return response.body().replace(embassyConfig.getQuoteCharacter(), StringUtils.EMPTY);
        } catch (Exception e) {
            log.error("Error occurred while fetching available dates. Message: {}", e.getMessage());
            return NO_DATE;
        }
    }

    private void handleInvalidCookie() {
        log.info("=================== Invalid cookie ===================");
        telegramService.sendTelegramInvalidCookieNotification();
        //playerService.playSound(sharedConfig.getRingtoneCrawling());

        // Retrieve and save a fresh cookie for the next scheduled check.
        retrieveAndSaveNewCookie();
    }

    private String checkSlots(List<HttpRequest> requests) {
        String slotFoundMessage = embassyConfig.getAvailableDatesMessage();
        String allSlotBookedMessage = embassyConfig.getAllSlotBookedMessage();
        if (requests.isEmpty()) {
            log.warn("Cannot check embassy appointment slots without available-date requests");
            return STATUS_4XX;
        }

        try {
            List<CompletableFuture<HttpResponse<String>>> responses = requests.stream()
                    .map(request -> client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                            .exceptionally(error -> {
                                log.warn("Available-date request failed for {}", request.uri(), error);
                                return null;
                            }))
                    .toList();
            CompletableFuture.allOf(responses.toArray(CompletableFuture[]::new)).join();

            String timestamp = LocalDateTime.now().toString();
            boolean hasSuccessfulResponse = false;
            boolean slotFound = false;
            for (int i = 0; i < responses.size(); i++) {
                HttpResponse<String> response = responses.get(i).join();
                if (response == null || response.statusCode() != 200 || response.body() == null) {
                    continue;
                }

                URI uri = requests.get(i).uri();
                log.info("Available Date API uri: {}, query:{} response: {}", uri, uri.getQuery(), response.body());
                hasSuccessfulResponse = true;
                String body = response.body().replace(embassyConfig.getQuoteCharacter(), StringUtils.EMPTY);
                if (body.contains(slotFoundMessage) || !body.contains(allSlotBookedMessage)) {
                    slotFound = true;
                    writeDatesInFile(body, uri.getQuery(), timestamp);
                }
            }

            if (!hasSuccessfulResponse) {
                log.info("Returning STATUS_4XX from apiRequestAvailableDates || {}", timestamp);
                return STATUS_4XX;
            }
            String result = slotFound ? STATUS_FOUND : STATUS_NOT_FOUND;
            log.info("Slot check completed. Result: {} || {}", result, timestamp);
            return result;
        } catch (Exception e) {
            log.error("Error occurred while checking available date, Message: {}", e.getMessage());
            return STATUS_4XX;
        }
    }

    private void writeDatesInFile(String dateString, String monthYear, String timestamp) {
        try (java.io.FileWriter writer = new java.io.FileWriter(embassyConfig.getDatesFile(), true)) {
            String temp = embassyConfig.getDateEntrySeparator();
            String valueSeparator = embassyConfig.getDateEntryValueSeparator();
            writer.write(temp + valueSeparator + timestamp + valueSeparator + monthYear
                    + valueSeparator + temp + System.lineSeparator());
            if (dateString != null) {
                writer.write(dateString + System.lineSeparator());
            }
        } catch (Exception e) {
            log.error("Failed to save dates: {}", e.getMessage());
        }
    }

    private void fillApplicationFormStep1AndStep2(Page page) {
        fillStep1Form(page);
        fillStep2Form(page);
    }

    private void fillApplicationFormFinalStep(Page page) {
        try {
            selectAppointmentDate(page);
            finalStep(page);
        } catch (Exception e) {
            log.error("An error occurred during fillApplicationFormFinalStep: {}", e.getMessage());
        }
    }

    private String retrieveAndSaveNewCookie() {
        String cookie = retrieveCookieFromApplicationForm();
        if (cookie != null && !cookie.isBlank()) {
            saveCookieToFile(cookie);
        }
        return cookie;
    }

    private String retrieveCookieFromApplicationForm() {
        String cookie;
        try (Playwright playwright = Playwright.create()) {
            Browser browser = initializeBrowser(playwright);
            BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(null));
            Page page = context.newPage();
            page.setDefaultTimeout(Duration.ofSeconds(WAIT_TIMEOUT).toMillis());
            // Complete Application Step 1 and Step 2
            fillApplicationFormStep1AndStep2(page);

            // Reaching Calendar Step 3 & extracting cookies
            page.waitForURL(embassyConfig.getStepThreeUrlPattern());
            page.locator(embassyConfig.getCalendarContainerSelector()).waitFor();
            page.waitForTimeout(1000);

            List<Cookie> allCookies = context.cookies();
            cookie = allCookies.stream()
                    .map(c -> c.name + embassyConfig.getCookieNameValueSeparator() + c.value)
                    .collect(Collectors.joining(embassyConfig.getCookieSeparator()));

            context.close();
            browser.close();
            return cookie;
        } catch (Exception e) {
            log.error("An error occurred while retrieving embassy appointment cookies, Message: {}", e.getMessage());
            return null;
        }
    }

    private Browser initializeBrowser(Playwright playwright) {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setArgs(Arrays.asList(embassyConfig.getBrowserArgs()));
        return playwright.chromium().launch(options);
    }

    private void fillStep1Form(Page page) {
        log.info("Opening Embassy Portal... Step 1");
        page.navigate(embassyConfig.getBaseUrl() + embassyConfig.getIndexPath());

        //log.info("Filling Visitor 1 details...");
        page.locator(embassyConfig.getFirstNameSelector()).fill(embassyConfig.getFirstName());
        page.locator(embassyConfig.getLastNameSelector()).fill(embassyConfig.getLastName());
        page.locator(embassyConfig.getEmailSelector()).fill(embassyConfig.getEmail());
        page.locator(embassyConfig.getEmailRepeatSelector()).fill(embassyConfig.getEmail());
        page.locator(embassyConfig.getPhoneSelector()).fill(embassyConfig.getPhone());
        page.locator(embassyConfig.getNextStepSelector()).click();
    }

    private void fillStep2Form(Page page) {
        log.info(embassyConfig.getStepTwoLogMessage());
        page.waitForURL(embassyConfig.getStepTwoUrlPattern());
        page.locator(embassyConfig.getServicesSelector()).click();

        page.locator(embassyConfig.getServiceLabelSelector(), new Page.LocatorOptions().setHasText(
                embassyConfig.getResidencePermitServiceLabel())).click();

        page.locator(embassyConfig.getActiveConfirmationSelector())
                .evaluate(embassyConfig.getConfirmationClickScript());

        page.locator(embassyConfig.getServiceButtonSelector()).click();
        //log.info("Add button clicked after Confirmation checkbox.");

        page.locator(embassyConfig.getNextStepSelector()).click();
        //log.info("Next step button clicked.");
        page.waitForTimeout(1000);
    }

    private String checkApiResponseViaBrowser(Page page, String targetUrl) {
        try {
            String response = (String) page.evaluate(
                    embassyConfig.getApiFetchScript(),
                    targetUrl);

            if (response != null) {
                // Remove leading/trailing quotes or internal escaped quotes if needed
                response = response.replace(embassyConfig.getQuoteCharacter(), StringUtils.EMPTY).trim();
            }
            return response;
        } catch (Exception e) {
            log.error("Failed to fetch API response via browser, Message: {}", e.getMessage());
            return null;
        }
    }

    private void selectAppointmentDate(Page page) {
        try {
            int monthsToCheck = 1;
            Locator nextButton = page.locator(embassyConfig.getCalendarNextSelector());
            for (int i = 0; i < 2; i++) {
                if (!nextButton.isVisible() || !nextButton.isEnabled()) {
                    log.info("Calendar next-month button is unavailable; stopping calendar navigation.");
                    break;
                }
                nextButton.click();
                monthsToCheck++;
                page.waitForTimeout(1000);
            }

            checkApiResponses(page, monthsToCheck);
            //page.waitForTimeout(10 * 1000);

            // Find all table cells that have the active class
            selectActiveCalendarDate(page);
        } catch (Exception e) {
            log.error("Exception occurred while checking appointment calendar dates, Message: {}", e.getMessage());
        }
    }

    private void checkApiResponses(Page page, int count) {
        String monthApiResponse = checkApiResponseViaBrowser(page, embassyConfig.getDateUrl());
        log.info("LastAvailableDate API [{}] | Response: {}", embassyConfig.getDateUrl(), monthApiResponse);

        checkAvailableMonthDatesApiResponse(page, count);
    }

    private void checkAvailableMonthDatesApiResponse(Page page, int count) {
        LocalDate ld = LocalDate.now();
        for (int i = 0; i < count; i++) {
            String availableMonthDatesUrl = new StringBuilder(embassyConfig.getAvailableDateUrl())
                    .append(ld.getYear())
                    .append(embassyConfig.getMonthQueryParameter())
                    .append(ld.getMonthValue())
                    .toString();

            String monthApiResponse = checkApiResponseViaBrowser(page, availableMonthDatesUrl);
            log.info("AvailableMonthDates API [{}] | Response: {}", availableMonthDatesUrl, monthApiResponse);
            ld = ld.plusMonths(1);
        }
    }

    private void selectActiveCalendarDate(Page page) {
        Locator availableDates = page.locator(embassyConfig.getActiveDateSelector());
        int count = availableDates.count();
        if (count > 0) {
            // Example: Click the very first available date
            Locator targetDate = availableDates.nth(count - 1);
            String dateValue = targetDate.getAttribute(embassyConfig.getDateAttributeName());

            targetDate.click();
            log.info("Successfully selected available date: {}", dateValue);
            writeDatesInFile(null, dateValue, LocalDateTime.now().toString());
        } else {
            log.info("No available dates found in the current view.");
        }
    }

    private void selectSpecificDate(Page page, String targetDate) {
        // Construct the CSS selector dynamically using the target date string (e.g., "2026-11-5")
        String cssSelector = embassyConfig.getSpecificDateSelectorTemplate().formatted(targetDate);

        try {
            // Wait until the specific date element is clickable and click it
            page.locator(cssSelector).click();
            writeDatesInFile(null, targetDate, LocalDateTime.now().toString());
            log.info("Successfully selected date: {}", targetDate);
        } catch (Exception e) {
            log.error("Target date {} was not clickable or not found: {}", targetDate, e.getMessage());
        }
    }

    private void finalStep(Page page) {
        log.info("FinalStep# providing InvitationNumber");
        //log.info("----------- finalStep completed -----------");
    }

    private List<HttpRequest> buildAvailableDatesRequest(String cookieString) {
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        List<HttpRequest> requests = new ArrayList<>(3);
        for (int monthOffset = 0; monthOffset < 3; monthOffset++) {
            LocalDate month = currentMonth.plusMonths(monthOffset);
            String url = embassyConfig.getAvailableDateUrl() + month.getYear()
                    + embassyConfig.getMonthQueryParameter() + month.getMonthValue();
            requests.add(HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header(USER_AGENT, USER_AGENT_VALUE)
                    .header(X_REQUESTED_WITH, X_REQUESTED_WITH_VALUE)
                    .header(COOKIE, cookieString)
                    .GET()
                    .build());
        }
        return requests;
    }

    private HttpRequest buildDateApiRequest(String cookie) {
        return HttpRequest.newBuilder()
                .uri(URI.create(embassyConfig.getDateUrl()))
                .header(USER_AGENT, USER_AGENT_VALUE)
                .header(X_REQUESTED_WITH, X_REQUESTED_WITH_VALUE)
                .header(COOKIE, cookie)
                .GET()
                .build();
    }

    private void saveCookieToFile(String cookieString) {
        log.info("Saving cookie to {}", embassyConfig.getCookiesFile());
        try (java.io.FileWriter writer = new java.io.FileWriter(embassyConfig.getCookiesFile())) {
            writer.write(cookieString);
        } catch (Exception e) {
            log.error("Failed to save cookie: {}", e.getMessage());
        }
    }

    private String loadCookieFromFile() {
        try {
            java.io.File file = new java.io.File(embassyConfig.getCookiesFile());
            if (!file.exists()) return null;
            return new String(java.nio.file.Files.readAllBytes(file.toPath())).trim();
        } catch (Exception e) {
            log.error("Failed to load cookie: {}", e.getMessage());
            return null;
        }
    }
}