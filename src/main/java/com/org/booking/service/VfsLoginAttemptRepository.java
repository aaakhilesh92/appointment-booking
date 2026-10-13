package com.org.booking.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VfsLoginAttemptRepository {

    private final JdbcTemplate jdbcTemplate;

    public VfsLoginAttemptRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(String email, boolean successful) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO vfs_login_attempt (email, successful) VALUES (?, ?) RETURNING id",
                Long.class,
                email,
                successful
        );
        if (id == null) {
            throw new IllegalStateException("PostgreSQL did not return the login attempt ID");
        }
        return id;
    }

    public void update(long attemptId, boolean successful) {
        jdbcTemplate.update(
                "UPDATE vfs_login_attempt SET successful = ?, modified_date = CURRENT_TIMESTAMP WHERE id = ?",
                successful,
                attemptId
        );
    }
}
