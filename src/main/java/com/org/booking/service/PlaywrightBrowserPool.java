package com.org.booking.service;

import java.util.Arrays;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.org.booking.config.EmbassyConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PlaywrightBrowserPool {
    private static final Logger log = LoggerFactory.getLogger(PlaywrightBrowserPool.class);

    private final EmbassyConfig embassyConfig;
    private final ThreadLocal<BrowserSession> sessions = new ThreadLocal<>();

    public PlaywrightBrowserPool(EmbassyConfig embassyConfig) {
        this.embassyConfig = embassyConfig;
    }

    public Browser getBrowser() {
        BrowserSession session = sessions.get();
        if (session == null) {
            session = startSession();
            sessions.set(session);
        }
        return session.browser();
    }

    public void closeCurrentThreadSession() {
        BrowserSession session = sessions.get();
        sessions.remove();
        if (session != null) {
            session.close();
        }
    }

    private BrowserSession startSession() {
        long playwrightStart = System.nanoTime();
        log.info("Starting Playwright for executor worker");
        Playwright playwright = Playwright.create();
        log.info("Started Playwright for executor worker in {} ms", elapsedMillis(playwrightStart));

        try {
            long browserStart = System.nanoTime();
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                    .setHeadless(false)
                    .setArgs(Arrays.asList(embassyConfig.getBrowserArgs()));
            Browser browser = playwright.chromium().launch(options);
            log.info("Launched browser for executor worker in {} ms", elapsedMillis(browserStart));
            return new BrowserSession(playwright, browser);
        } catch (RuntimeException e) {
            playwright.close();
            throw e;
        }
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private record BrowserSession(Playwright playwright, Browser browser) {
        private void close() {
            RuntimeException closeFailure = null;
            try {
                browser.close();
            } catch (RuntimeException e) {
                closeFailure = e;
            }
            try {
                playwright.close();
            } catch (RuntimeException e) {
                if (closeFailure == null) {
                    closeFailure = e;
                } else {
                    closeFailure.addSuppressed(e);
                }
            }
            if (closeFailure != null) {
                log.error("Failed to close Playwright session for executor worker", closeFailure);
            }
        }
    }
}
