# appointment-booking
Appointment Booking Process Automation

## VFS login attempt tracking

The application stores each VFS login submission in PostgreSQL in the
`vfs_login_attempt` table. It records the configured email and the database
timestamp immediately before submitting the login form, then sets `successful`
to `true` or `false` after the login result is determined. A `NULL` value means
the login result has not yet been recorded. The table and email index are
created automatically at startup. Login attempts also store `insert_date` and
`modified_date`; the latter is refreshed when the login result is updated.

Each alert displayed during a VFS slot check is stored in the
`vfs_slot_alert` table with its email, `alert_message`, `insert_date`, and
`modified_date`. Both tables are created automatically in PostgreSQL's
`public` schema.

By default, the application connects to `jdbc:postgresql://localhost:5432/postgres`
on `localhost:5432` as `postgres` (matching the pgAdmin connection shown).
The `public` schema is used, and PostgreSQL creates the tracking table at
startup. Set `DB_PASSWORD` to the PostgreSQL user's password if one is
configured. Override the connection settings with `DB_URL`, `DB_USERNAME`,
and `DB_PASSWORD`.

Count attempts for one email over the last 12 or 24 hours:

```sql
SELECT COUNT(*) AS attempt_count
FROM vfs_login_attempt
WHERE email = 'someone@example.com'
  AND insert_date >= CURRENT_TIMESTAMP - INTERVAL '12 hours';
```

Change `12 hours` to `24 hours` for a 24-hour count. To see counts for all
emails, group by `email` and include `email` in the selected columns.
