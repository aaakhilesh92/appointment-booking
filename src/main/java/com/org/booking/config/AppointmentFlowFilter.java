package com.org.booking.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

import static com.org.booking.config.Constants.APPOINTMENT_FLOW;

public class AppointmentFlowFilter extends Filter<ILoggingEvent> {

    private String flow;
    private boolean exclude;

    public void setFlow(String flow) {
        this.flow = flow;
    }

    public void setExclude(boolean exclude) {
        this.exclude = exclude;
    }

    @Override
    public FilterReply decide(ILoggingEvent event) {
        String eventFlow = event.getMDCPropertyMap().get(APPOINTMENT_FLOW);
        boolean matches = flow != null && flow.equals(eventFlow);
        if (exclude) {
            return matches ? FilterReply.DENY : FilterReply.NEUTRAL;
        }
        return matches ? FilterReply.NEUTRAL : FilterReply.DENY;
    }
}
