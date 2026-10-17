package com.org.booking.service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
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
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import static com.org.booking.config.Constants.*;

@Service
public class AppointmentService {
    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);
    private static final BufferedReader CONSOLE_INPUT = new BufferedReader(new InputStreamReader(System.in));
    private static final URI INTERNET_TIME_URI = URI.create("https://www.google.com/generate_204");
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final ZoneId EET_ZONE = ZoneId.of("Europe/Riga");
    private static final LocalTime BOOKING_TIME_IST = LocalTime.of(9, 0);
    private static final Duration BOOKING_WINDOW = Duration.ofMinutes(TIME_WINDOW);
    private static final DateTimeFormatter INTERNET_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z (XXX)");

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final AtomicLong lastNoSlotNotificationTime = new AtomicLong();
    private final AtomicReference<LocalDate> lastBookingDate = new AtomicReference<>();

    @Autowired
    private EmailService emailService;

    @Autowired
    private EmbassyConfig embassyConfig;

    @Autowired
    private PlaywrightBrowserPool playwrightBrowserPool;

    @Autowired
    private SharedConfig sharedConfig;

    @Autowired
    private TelegramService telegramService;

    @Autowired
    private MediaPlayerService playerService;

    @Autowired
    @Qualifier("appointmentBookingExecutor")
    private Executor appointmentBookingExecutor;

    @Autowired
    private Tracer tracer;

    public void bookEmbassyAppointment() throws IOException, InterruptedException {
        if (!waitUntilBookingTime()) {
            log.info("Skipping embassy booking: not within the 15-minute booking window");
            return;
        }

        List<Candidate> candidates = loadCandidates();
        CompletableFuture<?>[] bookingTasks = candidates.stream().map(candidate -> CompletableFuture.runAsync(() -> bookAppointmentForCandidate(candidate), appointmentBookingExecutor)).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(bookingTasks).join();
    }

    private void bookAppointmentForCandidate(Candidate candidate) {
        Span span = tracer.nextSpan().name("embassy-candidate-booking").start();
        String previousFlow = MDC.get(APPOINTMENT_FLOW);
        MDC.put(APPOINTMENT_FLOW, EMBASSY);
        long candidateStartNanos = System.nanoTime();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            log.info("Starting appointment booking process for candidate: {}", candidate.firstName());
            try {
                Browser browser = playwrightBrowserPool.getBrowser();
                BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(null));
                try {
                    Page page = context.newPage();
                    page.setDefaultTimeout(Duration.ofSeconds(WAIT_TIMEOUT).toMillis());
                    // Complete Application Step 1 and Step 2
                    fillApplicationFormStep1AndStep2(page, candidate);
                    // Complete Final Step and submit the form
                    fillApplicationFormFinalStep(page, candidate);
                } finally {
                    context.close();
                }
            } catch (Exception e) {
                log.error("An Exception occurred during bookEmbassyAppointment for candidate {}, Message: {}",
                        candidate.firstName(), e.getMessage(), e);
            }
            log.info("Candidate booking process finished for {} in {} ms",
                    candidate.firstName(), elapsedMillis(candidateStartNanos));
        } finally {
            span.end();
            if (previousFlow == null) {
                MDC.remove(APPOINTMENT_FLOW);
            } else {
                MDC.put(APPOINTMENT_FLOW, previousFlow);
            }
        }
    }

    private boolean waitUntilBookingTime() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(INTERNET_TIME_URI).timeout(Duration.ofSeconds(5)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Internet time request returned HTTP " + response.statusCode());
        }

        String dateHeader = response.headers().firstValue("Date").orElseThrow(() -> new IOException("Internet time response has no Date header"));
        Instant internetInstant;
        try {
            internetInstant = DateTimeFormatter.RFC_1123_DATE_TIME.parse(dateHeader, Instant::from);
        } catch (DateTimeParseException e) {
            throw new IOException("Internet time response has an invalid Date header", e);
        }

        ZonedDateTime istNow = internetInstant.atZone(IST_ZONE);
        ZonedDateTime eetNow = internetInstant.atZone(EET_ZONE);
        log.info("Internet time | IST: {} | Europe/Riga (EET/EEST): {}", INTERNET_TIME_FORMAT.format(istNow), INTERNET_TIME_FORMAT.format(eetNow));

        LocalDate bookingDate = istNow.toLocalDate();
        if (bookingDate.equals(lastBookingDate.get())) {
            log.info("Skipping embassy booking: today's 9:00 AM IST run has already started.");
            return false;
        }

        Instant targetInstant = bookingDate.atTime(BOOKING_TIME_IST).atZone(IST_ZONE).toInstant();
        Duration waitDuration = Duration.between(internetInstant, targetInstant);
        if (waitDuration.compareTo(BOOKING_WINDOW) > 0) {
            log.info("Skipping embassy booking: internet time is more than 15 minutes before 9:00 AM IST.");
            return false;
        }

        if (!waitDuration.isZero()) {
            log.info("Within the 15-minute booking window; waiting {} ms until 9:00 AM IST.", waitDuration.toMillis());
            playerService.playSound(sharedConfig.getRingtoneCrawling());
            long remainingNanos = waitDuration.toNanos();
            long waitStartedNanos = System.nanoTime();
            while (remainingNanos > 0) {
                long millis = remainingNanos / 1_000_000;
                int nanos = (int) (remainingNanos % 1_000_000);
                try {
                    Thread.sleep(millis, nanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                remainingNanos = waitDuration.toNanos() - (System.nanoTime() - waitStartedNanos);
            }
        }

        while (true) {
            LocalDate previousRun = lastBookingDate.get();
            if (bookingDate.equals(previousRun)) {
                log.info("Skipping embassy booking: today's 9:00 AM IST run has already started.");
                return false;
            }
            if (lastBookingDate.compareAndSet(previousRun, bookingDate)) {
                break;
            }
        }

        log.info("Starting embassy booking flow at the 9:00 AM IST target for {}.", bookingDate);
        return true;
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

    private List<Candidate> loadCandidates() {
        // Load candidate details from candidates.txt
        List<Candidate> candidates = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("candidates.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    candidates.add(new Candidate(parts[0], parts[1], parts[2], parts[3], parts[4]));
                }
            }
        } catch (IOException e) {
            log.error("Error occurred while loading candidates from file. Message: {}", e.getMessage());
        }
        return candidates;
    }

    private Candidate loadCandidate() {
        return new Candidate(embassyConfig.getFirstName(), embassyConfig.getLastName(), embassyConfig.getEmail(), embassyConfig.getPhone(), embassyConfig.getInvitationNumber());
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
            log.info("Skipping Embassy no-slot notification; next notification is allowed in {} seconds.", (notificationInterval - (now - lastNotification)) / 1000);
            return;
        }

        String availableDates = fetchAvailableDates(cookie);
        log.info("Embassy no-slot notification: available dates response: {}", availableDates);
        if (telegramService.sendTelegramNoSlotNotification(EMBASSY, availableDates)) {
            lastNoSlotNotificationTime.set(System.currentTimeMillis());
        } else {
            log.warn("Embassy no-slot notification failed; it will be retried on the next check.");
        }
        //playerService.playSound(sharedConfig.getRingtoneNotification());
    }

    private String fetchAvailableDates(String cookie) {
        try {
            HttpResponse<String> response = client.send(buildDateApiRequest(cookie), HttpResponse.BodyHandlers.ofString());
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
            List<CompletableFuture<HttpResponse<String>>> responses = requests.stream().map(request -> client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).exceptionally(error -> {
                log.warn("Available-date request failed for {}", request.uri(), error);
                return null;
            })).toList();
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
            writer.write(temp + valueSeparator + timestamp + valueSeparator + monthYear + valueSeparator + temp + System.lineSeparator());
            if (dateString != null) {
                writer.write(dateString + System.lineSeparator());
            }
        } catch (Exception e) {
            log.error("Failed to save dates: {}", e.getMessage());
        }
    }

    private void fillApplicationFormStep1AndStep2(Page page, Candidate candidate) {
        fillStep1Form(page, candidate);
        fillStep2Form(page);
    }

    private void fillApplicationFormFinalStep(Page page, Candidate candidate) {
        try {
            String windowTitle = "Appointment - " + candidate.firstName();
            page.evaluate("title => { document.title = title; }", windowTitle);
            log.info("Manual invitation number entry will be in the Chrome window titled '{}'.", windowTitle);
            selectAppointmentDate(page, candidate);
            finalStep(page, candidate);
        } catch (Exception e) {
            log.error("An error occurred during fillApplicationFormFinalStep for {}: {}", candidate.firstName(), e.getMessage());
        }
    }

    private String retrieveAndSaveNewCookie() {
        String cookie = retrieveCookieFromApplicationForm(loadCandidate());
        if (cookie != null && !cookie.isBlank()) {
            saveCookieToFile(cookie);
        }
        return cookie;
    }

    private String retrieveCookieFromApplicationForm(Candidate candidate) {
        String cookie;
        try (Playwright playwright = Playwright.create()) {
            Browser browser = initializeBrowser(playwright);
            BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(null));
            Page page = context.newPage();
            page.setDefaultTimeout(Duration.ofSeconds(WAIT_TIMEOUT).toMillis());
            // Complete Application Step 1 and Step 2
            fillApplicationFormStep1AndStep2(page, candidate);

            // Reaching Calendar Step 3 & extracting cookies
            page.waitForURL(embassyConfig.getStepThreeUrlPattern());
            page.locator(embassyConfig.getCalendarContainerSelector()).waitFor();
            page.waitForTimeout(1000);

            List<Cookie> allCookies = context.cookies();
            cookie = allCookies.stream().map(c -> c.name + embassyConfig.getCookieNameValueSeparator() + c.value).collect(Collectors.joining(embassyConfig.getCookieSeparator()));

            context.close();
            browser.close();
            return cookie;
        } catch (Exception e) {
            log.error("An error occurred while retrieving embassy appointment cookies, Message: {}", e.getMessage());
            return null;
        }
    }

    private Browser initializeBrowser(Playwright playwright) {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(false).setArgs(Arrays.asList(embassyConfig.getBrowserArgs()));
        return playwright.chromium().launch(options);
    }

    private void fillStep1Form(Page page, Candidate candidate) {
        log.info("Opening Embassy Portal... Step 1");
        page.navigate(embassyConfig.getBaseUrl() + embassyConfig.getIndexPath());

        //log.info("Filling Visitor 1 details...");
        page.locator(embassyConfig.getFirstNameSelector()).fill(candidate.firstName());
        page.locator(embassyConfig.getLastNameSelector()).fill(candidate.lastName());
        page.locator(embassyConfig.getEmailSelector()).fill(candidate.email());
        page.locator(embassyConfig.getEmailRepeatSelector()).fill(candidate.email());
        page.locator(embassyConfig.getPhoneSelector()).fill(candidate.phone());
        page.locator(embassyConfig.getNextStepSelector()).click();
    }

    private void fillStep2Form(Page page) {
        log.info(embassyConfig.getStepTwoLogMessage());
        page.waitForURL(embassyConfig.getStepTwoUrlPattern());
        page.locator(embassyConfig.getServicesSelector()).click();

        page.locator(embassyConfig.getServiceLabelSelector(), new Page.LocatorOptions().setHasText(embassyConfig.getResidencePermitServiceLabel())).click();

        page.locator(embassyConfig.getActiveConfirmationSelector()).evaluate(embassyConfig.getConfirmationClickScript());

        page.locator(embassyConfig.getServiceButtonSelector()).click();
        //log.info("Add button clicked after Confirmation checkbox.");

        page.locator(embassyConfig.getNextStepSelector()).click();
        //log.info("Next step button clicked.");
        page.waitForTimeout(100);
    }

    private String checkApiResponseViaBrowser(Page page, String targetUrl) {
        try {
            String response = (String) page.evaluate(embassyConfig.getApiFetchScript(), targetUrl);

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

    private void selectAppointmentDate(Page page, Candidate candidate) {
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
                page.waitForTimeout(500);
            }

            boolean slotFound = checkApiResponses(page, monthsToCheck);
            if (slotFound) {
                //waitForManualInput(candidate);
                page.waitForTimeout(30 * 1000);
                log.info("!!! EMBASSY SLOTS FOUND for month !!! Check the website immediately.");
            }
            if (candidate.firstName().equalsIgnoreCase("Akhilesh")) {
                selectActiveCalendarDate(page, candidate);
                //selectSpecificDate(page, "2026-12-15");
            }
        } catch (Exception e) {
            log.error("Exception occurred while checking appointment calendar dates, Message: {}", e.getMessage());
        }
    }

    private boolean checkApiResponses(Page page, int count) {
        String monthApiResponse = checkApiResponseViaBrowser(page, embassyConfig.getDateUrl());
        log.info("LastAvailableDate API [{}] | Response: {}", embassyConfig.getDateUrl(), monthApiResponse);

        return checkAvailableMonthDatesApiResponse(page, count);
    }

    private boolean checkAvailableMonthDatesApiResponse(Page page, int count) {
        LocalDate ld = LocalDate.now();
        boolean slotFound = false;
        for (int i = 0; i < count; i++) {
            String availableMonthDatesUrl = new StringBuilder(embassyConfig.getAvailableDateUrl())
                    .append(ld.getYear())
                    .append(embassyConfig.getMonthQueryParameter())
                    .append(ld.getMonthValue())
                    .toString();

            String monthApiResponse = checkApiResponseViaBrowser(page, availableMonthDatesUrl);
            if (monthApiResponse != null && !monthApiResponse.contains(embassyConfig.getAllSlotBookedMessage())) {
                slotFound = true;
            }
            log.info("AvailableMonthDates API [{}] | Response: {}", availableMonthDatesUrl, monthApiResponse);
            ld = ld.plusMonths(1);
        }
        return slotFound;
    }

    private <T> T timed(String step, Supplier<T> action) {
        long startNanos = System.nanoTime();
        log.info("Starting step: {}", step);
        try {
            T result = action.get();
            log.info("Completed step: {} in {} ms", step, elapsedMillis(startNanos));
            return result;
        } catch (RuntimeException e) {
            log.error("Step failed: {} after {} ms", step, elapsedMillis(startNanos), e);
            throw e;
        }
    }

    private void timedAction(String step, Runnable action) {
        timed(step, () -> {
            action.run();
            return null;
        });
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private void selectActiveCalendarDate(Page page, Candidate candidate) {
        log.info("Selecting an available date from the calendar...");
        try {
            Locator availableDates = page.locator(embassyConfig.getActiveDateSelector());
            int count = availableDates.count();
            saveHtmlSnapshot(page, candidate, "selectActiveCalendarDate");
            log.info("Number of available dates found: {} for {}", count, candidate.firstName());

            if (count > 0) {
                String dateValueAll = "";
                for (int i = 0; i < count; i++) {
                    Locator dateLocator = availableDates.nth(i);
                    String dateValue = dateLocator.getAttribute(embassyConfig.getDateAttributeName());
                    log.info("Available date found: {}", dateValue);
                    dateValueAll += dateValue + ", ";
                }

                // Example: Click the very first available date
                Locator targetDate = availableDates.nth(count - 1);
                String dateValue = targetDate.getAttribute(embassyConfig.getDateAttributeName());

                targetDate.click();
                log.info("Successfully selected available date: {}", dateValue);
                writeDatesInFile(null, dateValueAll, LocalDateTime.now().toString());
            } else {
                log.info("No available dates found in the current view.");
            }
        } catch (Exception e) {
            log.error("Failed to select an available date: {}", e.getMessage());
        }
    }

    private void selectSpecificDate(Page page, String targetDate) {
        // Construct the CSS selector dynamically using the target date string (e.g., "2026-11-5")
        String cssSelector = embassyConfig.getSpecificDateSelectorTemplate().formatted(targetDate);

        try {
            log.info("Attempting to select specific date: {} using selector: {}", targetDate, cssSelector);
            // Wait until the specific date element is clickable and click it
            page.locator(cssSelector).click();
            writeDatesInFile(null, targetDate, LocalDateTime.now().toString());
            log.info("Successfully selected date: {}", targetDate);
        } catch (Exception e) {
            log.error("Target date {} was not clickable or not found: {}", targetDate, e.getMessage());
        }
    }

    private void finalStep(Page page, Candidate candidate) {
        saveHtmlSnapshot(page, candidate, "finalStep");
        log.info("FinalStep# providing InvitationNumber for candidate: {}", candidate.firstName());
        //log.info("----------- finalStep completed -----------");
    }

    private void saveHtmlSnapshot(Page page, Candidate candidate, String stage) {
        try {
            String candidateFolder = (candidate.firstName() + "_" + candidate.lastName()).replaceAll("[^a-zA-Z0-9._-]", "_");
            String timestamp = ZonedDateTime.now(EET_ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS"));
            Path snapshotDirectory = Path.of("html", candidateFolder, timestamp);
            Files.createDirectories(snapshotDirectory);

            Path snapshotPath = snapshotDirectory.resolve(stage + ".html");
            String html = page.content();
            int headStart = html.toLowerCase(Locale.ROOT).indexOf("<head");
            int headEnd = headStart < 0 ? -1 : html.indexOf('>', headStart);
            if (headEnd < 0) {
                throw new IOException("Page HTML has no head element for setting the asset base URL.");
            }
            String baseHref = URI.create(page.url()).resolve("/").toString();
            html = html.substring(0, headEnd + 1) + "<base href=\"" + baseHref + "\">" + html.substring(headEnd + 1);
            Files.writeString(snapshotPath, html, StandardCharsets.UTF_8);
            log.info("Saved {} page HTML snapshot to {}", stage, snapshotPath.toAbsolutePath());
        } catch (Exception e) {
            log.info("Failed to save HTML snapshot for candidate {} at stage {}: {}", candidate.firstName(), stage, e.getMessage());
        }
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