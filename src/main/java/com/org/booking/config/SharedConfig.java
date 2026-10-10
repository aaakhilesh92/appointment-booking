package com.org.booking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SharedConfig {

    @Value("${app.player.path}")
    private String playerPath;

    @Value("${app.audio.siren}")
    private String ringtoneSiren;

    @Value("${app.audio.crawling}")
    private String ringtoneCrawling;

    @Value("${app.audio.notification}")
    private String ringtoneNotification;

    @Value("${app.mail.username}")
    private String mailUserName;

    @Value("${app.mail.password}")
    private String mailPassword;

    @Value("${app.mail.recipients}")
    private String mailRecipients;

    @Value("${app.telegram.bot-token}")
    private String telegramBotToken;

    @Value("${app.telegram.chat-id}")
    private String telegramChatId;

    @Value("${app.telegram.no-slot-bot-token}")
    private String telegramNoSlotBotToken;

    @Value("${app.telegram.no-slot-chat-id}")
    private String telegramNoSlotChatId;

    @Value("${app.telegram.no-slot-notification-interval-ms:900000}")
    private long noSlotNotificationIntervalMs;

    public String getPlayerPath() {
        return playerPath;
    }

    public String getRingtoneSiren() {
        return ringtoneSiren;
    }

    public String getRingtoneCrawling() {
        return ringtoneCrawling;
    }

    public String getRingtoneNotification() {
        return ringtoneNotification;
    }

    public String getMailUserName() {
        return mailUserName;
    }

    public String getMailPassword() {
        return mailPassword;
    }

    public String getMailRecipients() {
        return mailRecipients;
    }

    public String getTelegramBotToken() {
        return telegramBotToken;
    }

    public String getTelegramChatId() {
        return telegramChatId;
    }

    public String getTelegramNoSlotBotToken() {
        return telegramNoSlotBotToken;
    }

    public String getTelegramNoSlotChatId() {
        return telegramNoSlotChatId;
    }

    public long getNoSlotNotificationIntervalMs() {
        if (noSlotNotificationIntervalMs <= 0) {
            throw new IllegalStateException(
                    "app.telegram.no-slot-notification-interval-ms must be greater than zero");
        }
        return noSlotNotificationIntervalMs;
    }
}
