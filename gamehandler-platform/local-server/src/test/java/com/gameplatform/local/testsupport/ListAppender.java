package com.gameplatform.local.testsupport;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Logback in-memory appender for capturing log events in unit tests.
 * (LogCaptor is forbidden in this project — use this instead.)
 */
public class ListAppender extends AppenderBase<ILoggingEvent> {

    private final List<ILoggingEvent> events = new ArrayList<>();

    @Override
    protected void append(ILoggingEvent eventObject) {
        events.add(eventObject);
    }

    public List<ILoggingEvent> getEvents() {
        return events;
    }

    public void reset() {
        events.clear();
    }
}
