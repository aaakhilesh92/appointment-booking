package com.org.booking.cron;

import com.org.booking.service.VfsAppointmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.org.booking.config.Constants.APPOINTMENT_FLOW;
import static com.org.booking.config.Constants.VFS;

@Component
@ConditionalOnProperty(name = "app.scheduler.vfs.enabled", havingValue = "true", matchIfMissing = true)
public class VFSAppointmentScheduler {

    private static final Logger log = LoggerFactory.getLogger(VFSAppointmentScheduler.class);

    @Autowired
    private VfsAppointmentService appointmentService;

    @Scheduled(fixedDelay = 60 * 60 * 1000)
    public void checkAppointment() {
        MDC.put(APPOINTMENT_FLOW, VFS);
        long startTime = System.currentTimeMillis();
        try {
            log.info("VFSAppointmentScheduler::checkAppointment starting now, currentTime: {}", LocalDateTime.now());
            appointmentService.checkVfsAppointment();
            log.info("VFSAppointmentScheduler::checkAppointment completed now and took {}ms",
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("VFSAppointmentScheduler::checkAppointment failed after {}ms",
                    System.currentTimeMillis() - startTime, e);
            throw e;
        } finally {
            MDC.remove(APPOINTMENT_FLOW);
        }
    }
}