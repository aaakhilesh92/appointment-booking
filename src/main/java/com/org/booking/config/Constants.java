package com.org.booking.config;

public interface Constants {
    String USER_AGENT = "User-Agent";
    String USER_AGENT_VALUE = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";
    String X_REQUESTED_WITH = "X-Requested-With";
    String X_REQUESTED_WITH_VALUE = "XMLHttpRequest";
    String COOKIE = "Cookie";

    String STATUS_4XX = "4XX";
    String STATUS_FOUND = "SLOT_AVAILABLE";
    String STATUS_NOT_FOUND = "SLOT_NOT_AVAILABLE";

    int WAIT_TIMEOUT = 10;
    long NO_SLOT_NOTIFICATION_INTERVAL_MS = 900000;
    long CHECK_INTERVAL_MS = 300000;

    String VFS = "VFS";
    String EMBASSY = "EMBASSY";
    String NO_DATE = "NO_DATE";

    String APPOINTMENT_FLOW = "appointmentFlow";

    int TIME_WINDOW = 15;
}
