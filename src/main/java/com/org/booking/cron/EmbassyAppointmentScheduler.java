package com.org.booking.cron;

import com.org.booking.service.AppointmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.org.booking.config.Constants.APPOINTMENT_FLOW;
import static com.org.booking.config.Constants.EMBASSY;

@Component
@ConditionalOnProperty(name = "app.scheduler.embassy.enabled", havingValue = "true", matchIfMissing = true)
public class EmbassyAppointmentScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmbassyAppointmentScheduler.class);

    @Autowired
    private AppointmentService appointmentService;

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void bookAppointment() throws Exception {
        MDC.put(APPOINTMENT_FLOW, EMBASSY);
        long startTime = System.currentTimeMillis();
        try {
            log.info("BookAppointmentScheduler::bookAppointment starting now, currentTime: {}", LocalDateTime.now());
            appointmentService.bookEmbassyAppointment();
            log.info("BookAppointmentScheduler::bookAppointment completed now and took {}ms",
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("BookAppointmentScheduler::bookAppointment failed after {}ms",
                    System.currentTimeMillis() - startTime, e);
            throw e;
        } finally {
            MDC.remove(APPOINTMENT_FLOW);
        }
    }

    @Scheduled(fixedRate = 60 * 1000)
    public void checkAppointment() throws Exception {
        MDC.put(APPOINTMENT_FLOW, EMBASSY);
        long startTime = System.currentTimeMillis();
        try {
            log.info("CheckAppointmentScheduler::checkAppointment starting now, currentTime: {}", LocalDateTime.now());
            appointmentService.checkEmbassyAppointment();
            log.info("CheckAppointmentScheduler::checkAppointment completed now and took {}ms",
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("CheckAppointmentScheduler::checkAppointment failed after {}ms",
                    System.currentTimeMillis() - startTime, e);
            throw e;
        } finally {
            MDC.remove(APPOINTMENT_FLOW);
        }
    }
}