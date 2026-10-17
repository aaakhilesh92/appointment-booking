package com.org.booking.config;

import com.org.booking.service.PlaywrightBrowserPool;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AppointmentExecutorConfig {
    private static final int POOL_SIZE = 4;

    @Bean(name = "appointmentBookingExecutor")
    public ThreadPoolTaskExecutor appointmentBookingExecutor(PlaywrightBrowserPool browserPool) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(POOL_SIZE);
        executor.setMaxPoolSize(POOL_SIZE);
        executor.setQueueCapacity(100);
        CustomizableThreadFactory threadFactory = new CustomizableThreadFactory("appointment-");
        executor.setThreadFactory(worker -> threadFactory.newThread(() -> {
            try {
                worker.run();
            } finally {
                browserPool.closeCurrentThreadSession();
            }
        }));
        return executor;
    }

    @Bean
    public ApplicationRunner initializePlaywrightBrowserSessions(
            PlaywrightBrowserPool browserPool,
            @Qualifier("appointmentBookingExecutor") ThreadPoolTaskExecutor executor) {
        return args -> browserPool.warmUp(executor, executor.getCorePoolSize());
    }
}
