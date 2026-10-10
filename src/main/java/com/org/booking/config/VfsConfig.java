package com.org.booking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VfsConfig {

    @Value("${app.vfs.url}")
    private String url;

    @Value("${app.vfs.username1}")
    private String username1;

    @Value("${app.vfs.password1}")
    private String password1;

    @Value("${app.vfs.username2}")
    private String username2;

    @Value("${app.vfs.password2}")
    private String password2;

    @Value("${app.vfs.login.email-selector}")
    private String loginEmailSelector;

    @Value("${app.vfs.login.password-selector}")
    private String loginPasswordSelector;

    @Value("${app.vfs.login.submit-selector}")
    private String loginSubmitSelector;

    @Value("${app.vfs.login.error-selector}")
    private String loginErrorSelector;

    @Value("${app.vfs.login.url-fragment}")
    private String loginUrlFragment;

    @Value("${app.vfs.page.popup-link-selector}")
    private String popupLinkSelector;

    @Value("${app.vfs.browser.args}")
    private String[] browserArgs;

    @Value("${app.vfs.browser.data-path}")
    private String browserDataPath;

    @Value("${app.vfs.page.cookie-accept-selector}")
    private String cookieAcceptSelector;

    @Value("${app.vfs.page.book-now-selector}")
    private String bookNowSelector;

    @Value("${app.vfs.page.start-booking-selector}")
    private String startBookingSelector;

    @Value("${app.vfs.page.subvisa-category-selector}")
    private String subvisaCategorySelector;

    @Value("${app.vfs.page.long-stay-option-selector}")
    private String longStayOptionSelector;

    @Value("${app.vfs.page.sub-category-selector}")
    private String subCategorySelector;

    @Value("${app.vfs.page.residence-permit-option-selector}")
    private String residencePermitOptionSelector;

    @Value("${app.vfs.page.slot-check-response-fragment}")
    private String slotCheckResponseFragment;

    @Value("${app.vfs.page.alert-selector}")
    private String alertSelector;

    @Value("${app.vfs.page.no-slots-message}")
    private String noSlotsMessage;

    @Value("${app.vfs.credentials.max-consecutive-failures:2}")
    private int maxConsecutiveFailures;

    public String getUrl() {
        return url;
    }

    public String getUsername1() {
        return username1;
    }

    public String getPassword1() {
        return password1;
    }

    public String getUsername2() {
        return username2;
    }

    public String getPassword2() {
        return password2;
    }

    public String getLoginEmailSelector() {
        return loginEmailSelector;
    }

    public String getLoginPasswordSelector() {
        return loginPasswordSelector;
    }

    public String getLoginSubmitSelector() {
        return loginSubmitSelector;
    }

    public String getLoginErrorSelector() {
        return loginErrorSelector;
    }

    public String getLoginUrlFragment() {
        return loginUrlFragment;
    }

    public String getPopupLinkSelector() {
        return popupLinkSelector;
    }

    public String[] getBrowserArgs() {
        return browserArgs;
    }

    public String getBrowserDataPath() {
        return browserDataPath;
    }

    public String getCookieAcceptSelector() {
        return cookieAcceptSelector;
    }

    public String getBookNowSelector() {
        return bookNowSelector;
    }

    public String getStartBookingSelector() {
        return startBookingSelector;
    }

    public String getSubvisaCategorySelector() {
        return subvisaCategorySelector;
    }

    public String getLongStayOptionSelector() {
        return longStayOptionSelector;
    }

    public String getSubCategorySelector() {
        return subCategorySelector;
    }

    public String getResidencePermitOptionSelector() {
        return residencePermitOptionSelector;
    }

    public String getSlotCheckResponseFragment() {
        return slotCheckResponseFragment;
    }

    public String getAlertSelector() {
        return alertSelector;
    }

    public String getNoSlotsMessage() {
        return noSlotsMessage;
    }

    public int getMaxConsecutiveFailures() {
        if (maxConsecutiveFailures < 1) {
            throw new IllegalStateException(
                    "app.vfs.credentials.max-consecutive-failures must be at least 1");
        }
        return maxConsecutiveFailures;
    }
}
