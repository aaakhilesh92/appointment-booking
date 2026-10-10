package com.org.booking.service;

import com.org.booking.config.SharedConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MediaPlayerService {
    private static final Logger log = LoggerFactory.getLogger(MediaPlayerService.class);

    @Autowired
    private SharedConfig config;

    public void playSound(String filePath) {
        try {
            new ProcessBuilder(config.getPlayerPath(), filePath, "--play-and-exit").start();
        } catch (Exception e) {
            log.error("Failed to play sound: " + e.getMessage());
        }
    }
}
