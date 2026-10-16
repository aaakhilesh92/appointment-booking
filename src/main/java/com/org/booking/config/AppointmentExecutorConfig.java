package com.org.booking.config;

import com.org.booking.service.PlaywrightBrowserPool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AppointmentExecutorConfig {

    @Bean(name = "appointmentBookingExecutor")
    public ThreadPoolTaskExecutor appointmentBookingExecutor(PlaywrightBrowserPool browserPool) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(8);
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
}
