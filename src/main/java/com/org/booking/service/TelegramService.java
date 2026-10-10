package com.org.booking.service;

import com.org.booking.config.EmbassyConfig;
import com.org.booking.config.SharedConfig;
import com.org.booking.config.VfsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.org.booking.config.Constants.EMBASSY;
import static com.org.booking.config.Constants.VFS;

@Component
public class TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);

    @Autowired
    private SharedConfig config;

    @Autowired
    private VfsConfig vfsConfig;

    @Autowired
    private EmbassyConfig embassyConfig;
    private final HttpClient client = HttpClient.newHttpClient();

    public void sendTelegramNotification(String flow) {
        sendMessage(config.getTelegramBotToken(), config.getTelegramChatId(),
                getFoundSlotMessage(flow), flow, "slot-available");
    }

    public boolean sendTelegramNoSlotNotification(String flow, String availableDate) {
        return sendMessage(config.getTelegramNoSlotBotToken(), config.getTelegramNoSlotChatId(),
                getNoSlotMessage(flow, availableDate), flow, "no-slot");
    }

    public void sendTelegramInvalidCookieNotification() {
        sendMessage(config.getTelegramNoSlotBotToken(), config.getTelegramNoSlotChatId(),
                "⚠️ Invalid cookie detected. Refreshing session...", EMBASSY, "invalid-cookie");
    }

    private boolean sendMessage(String botToken, String chatId, String message, String flow, String notificationType) {
        try {
            String formData = "chat_id=" + encodeFormValue(chatId) + "&text=" + encodeFormValue(message);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + botToken + "/sendMessage"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("Telegram {} notification sent for {} flow.", notificationType, flow);
                return true;
            } else {
                log.error("Telegram {} notification failed for {} flow with status {}: {}",
                        notificationType, flow, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Failed to send Telegram {} notification for {} flow, Message: {}", notificationType, flow, e.getMessage());
        }
        return false;
    }

    private String encodeFormValue(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String getUrl(String flow) {
        return switch (flow) {
            case VFS -> vfsConfig.getUrl();
            case EMBASSY -> embassyConfig.getBaseUrl();
            default -> "generic-url";
        };
    }

    private String getNoSlotMessage(String flow, String availableDate) {
        return switch (flow) {
            case VFS -> "ℹ️ No VFS slots available.";
            case EMBASSY -> "ℹ️ No EMBASSY slots available. All Slots booked till " + availableDate;
            default -> "ℹ️ No slots available.";
        };
    }

    private String getFoundSlotMessage(String flow) {
        return switch (flow) {
            case VFS -> "🚨 *  c* 🚨\n\n VFS Slots are now available!\nCheck immediately: " + getUrl(flow);
            case EMBASSY -> "🚨 *  c* 🚨\n\nEMBASSY Slots are now available!\nCheck immediately: " + getUrl(flow);
            default -> "🚨 *  c* 🚨\n\nNew slots are available!";
        };
    }
}
