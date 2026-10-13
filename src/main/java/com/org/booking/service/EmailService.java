package com.org.booking.service;

import com.org.booking.config.EmbassyConfig;
import com.org.booking.config.SharedConfig;
import com.org.booking.config.VfsConfig;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Properties;

import static com.org.booking.config.Constants.EMBASSY;
import static com.org.booking.config.Constants.VFS;

@Component
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private SharedConfig config;

    @Autowired
    private VfsConfig vfsConfig;

    @Autowired
    private EmbassyConfig embassyConfig;

    public void sendEmail(String flow) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(config.getMailUserName(), config.getMailPassword());
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(config.getMailUserName()));
            message.setRecipients(MimeMessage.RecipientType.TO, InternetAddress.parse(String.join(",", config.getMailRecipients())));
            message.setSubject(getSubject(flow));
            message.setText("Slots are now available! Check the website immediately: " + getUrl(flow));
            Transport.send(message);
            log.info("Email sent successfully to {} recipients!", config.getMailRecipients());
        } catch (MessagingException e) {
            log.error("Failed to send email: {}", e.getMessage());
        }
    }

    private String getSubject(String flow) {
        return switch (flow) {
            case VFS -> "SLOTS AVAILABLE - VFS Appointment";
            case EMBASSY -> "SLOTS AVAILABLE - Embassy Appointment";
            default -> "SLOTS AVAILABLE - Appointment";
        };
    }

    private String getUrl(String flow) {
        return switch (flow) {
            case VFS -> vfsConfig.getUrl();
            case EMBASSY -> embassyConfig.getBaseUrl();
            default -> "general-appointment-url";
        };
    }
}
