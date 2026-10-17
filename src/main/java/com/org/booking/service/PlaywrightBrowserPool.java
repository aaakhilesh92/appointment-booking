package com.org.booking.service;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

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

    public void warmUp(Executor executor, int workerCount) {
        CountDownLatch sessionsCreated = new CountDownLatch(workerCount);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        CompletableFuture<?>[] warmupTasks = new CompletableFuture<?>[workerCount];

        for (int i = 0; i < workerCount; i++) {
            warmupTasks[i] = CompletableFuture.runAsync(() -> {
                try {
                    getBrowser();
                } finally {
                    sessionsCreated.countDown();
                }
                try {
                    releaseWorkers.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while warming up Playwright sessions", e);
                }
            }, executor);
        }

        try {
            sessionsCreated.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            releaseWorkers.countDown();
            throw new IllegalStateException("Interrupted while waiting for Playwright sessions to start", e);
        }

        releaseWorkers.countDown();
        CompletableFuture.allOf(warmupTasks).join();
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
