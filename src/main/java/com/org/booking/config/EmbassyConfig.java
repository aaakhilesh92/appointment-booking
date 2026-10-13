package com.org.booking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EmbassyConfig {

    @Value("${app.embassy.url.base}")
    private String baseUrl;

    @Value("${app.embassy.url.date}")
    private String dateUrl;

    @Value("${app.embassy.url.available-month-dates-year}")
    private String availableDateUrl;

    @Value("${app.embassy.cookies.file}")
    private String cookiesFile;

    @Value("${app.embassy.dates.file}")
    private String datesFile;

    @Value("${app.embassy.invitation.number}")
    private String invitationNumber;

    @Value("${app.embassy.browser.args}")
    private String[] browserArgs;

    @Value("${app.embassy.page.step-three-url-pattern}")
    private String stepThreeUrlPattern;

    @Value("${app.embassy.page.step-two-url-pattern}")
    private String stepTwoUrlPattern;

    @Value("${app.embassy.page.calendar-container-selector}")
    private String calendarContainerSelector;

    @Value("${app.embassy.page.first-name-selector}")
    private String firstNameSelector;

    @Value("${app.embassy.page.last-name-selector}")
    private String lastNameSelector;

    @Value("${app.embassy.page.email-selector}")
    private String emailSelector;

    @Value("${app.embassy.page.email-repeat-selector}")
    private String emailRepeatSelector;

    @Value("${app.embassy.page.phone-selector}")
    private String phoneSelector;

    @Value("${app.embassy.page.next-step-selector}")
    private String nextStepSelector;

    @Value("${app.embassy.page.services-selector}")
    private String servicesSelector;

    @Value("${app.embassy.page.step-two-log-message}")
    private String stepTwoLogMessage;

    @Value("${app.embassy.page.service-label-selector}")
    private String serviceLabelSelector;

    @Value("${app.embassy.page.residence-permit-service-label}")
    private String residencePermitServiceLabel;

    @Value("${app.embassy.page.active-confirmation-selector}")
    private String activeConfirmationSelector;

    @Value("${app.embassy.page.confirmation-click-script}")
    private String confirmationClickScript;

    @Value("${app.embassy.page.service-button-selector}")
    private String serviceButtonSelector;

    @Value("${app.embassy.page.calendar-next-selector}")
    private String calendarNextSelector;

    @Value("${app.embassy.page.active-date-selector}")
    private String activeDateSelector;

    @Value("${app.embassy.page.date-attribute-name}")
    private String dateAttributeName;

    @Value("${app.embassy.page.specific-date-selector-template}")
    private String specificDateSelectorTemplate;

    @Value("${app.embassy.page.index-path}")
    private String indexPath;

    @Value("${app.embassy.api.fetch-script}")
    private String apiFetchScript;

    @Value("${app.embassy.api.available-dates-message}")
    private String availableDatesMessage;

    @Value("${app.embassy.api.all-slot-booked-message}")
    private String allSlotBookedMessage;

    @Value("${app.embassy.api.month-query-parameter}")
    private String monthQueryParameter;

    @Value("${app.embassy.api.quote-character}")
    private String quoteCharacter;

    @Value("${app.embassy.files.date-entry-separator}")
    private String dateEntrySeparator;

    @Value("${app.embassy.files.date-entry-value-separator}")
    private String dateEntryValueSeparator;

    @Value("${app.embassy.cookies.name-value-separator}")
    private String cookieNameValueSeparator;

    @Value("${app.embassy.cookies.separator}")
    private String cookieSeparator;

    @Value("${app.embassy.form.first-name}")
    private String firstName;

    @Value("${app.embassy.form.last-name}")
    private String lastName;

    @Value("${app.embassy.form.email}")
    private String email;

    @Value("${app.embassy.form.phone}")
    private String phone;

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getDateUrl() {
        return dateUrl;
    }

    public String getAvailableDateUrl() {
        return availableDateUrl;
    }

    public String getCookiesFile() {
        return cookiesFile;
    }

    public String getDatesFile() {
        return datesFile;
    }

    public String getInvitationNumber() {
        return invitationNumber;
    }

    public String[] getBrowserArgs() {
        return browserArgs;
    }

    public String getStepThreeUrlPattern() {
        return stepThreeUrlPattern;
    }

    public String getStepTwoUrlPattern() {
        return stepTwoUrlPattern;
    }

    public String getCalendarContainerSelector() {
        return calendarContainerSelector;
    }

    public String getFirstNameSelector() {
        return firstNameSelector;
    }

    public String getLastNameSelector() {
        return lastNameSelector;
    }

    public String getEmailSelector() {
        return emailSelector;
    }

    public String getEmailRepeatSelector() {
        return emailRepeatSelector;
    }

    public String getPhoneSelector() {
        return phoneSelector;
    }

    public String getNextStepSelector() {
        return nextStepSelector;
    }

    public String getServicesSelector() {
        return servicesSelector;
    }

    public String getStepTwoLogMessage() {
        return stepTwoLogMessage;
    }

    public String getServiceLabelSelector() {
        return serviceLabelSelector;
    }

    public String getAllSlotBookedMessage() {
        return allSlotBookedMessage;
    }

    public String getResidencePermitServiceLabel() {
        return residencePermitServiceLabel;
    }

    public String getActiveConfirmationSelector() {
        return activeConfirmationSelector;
    }

    public String getConfirmationClickScript() {
        return confirmationClickScript;
    }

    public String getServiceButtonSelector() {
        return serviceButtonSelector;
    }

    public String getCalendarNextSelector() {
        return calendarNextSelector;
    }

    public String getActiveDateSelector() {
        return activeDateSelector;
    }

    public String getDateAttributeName() {
        return dateAttributeName;
    }

    public String getSpecificDateSelectorTemplate() {
        return specificDateSelectorTemplate;
    }

    public String getIndexPath() {
        return indexPath;
    }

    public String getApiFetchScript() {
        return apiFetchScript;
    }

    public String getAvailableDatesMessage() {
        return availableDatesMessage;
    }

    public String getMonthQueryParameter() {
        return monthQueryParameter;
    }

    public String getQuoteCharacter() {
        return quoteCharacter;
    }

    public String getDateEntrySeparator() {
        return dateEntrySeparator;
    }

    public String getDateEntryValueSeparator() {
        return dateEntryValueSeparator;
    }

    public String getCookieNameValueSeparator() {
        return cookieNameValueSeparator;
    }

    public String getCookieSeparator() {
        return cookieSeparator;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }
}
