package com.org.booking.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VfsSlotAlertRepository {

    private final JdbcTemplate jdbcTemplate;

    public VfsSlotAlertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(String email, String alertMessage) {
        jdbcTemplate.update(
                "INSERT INTO vfs_slot_alert (email, alert_message) VALUES (?, ?)",
                email,
                alertMessage
        );
    }
}
