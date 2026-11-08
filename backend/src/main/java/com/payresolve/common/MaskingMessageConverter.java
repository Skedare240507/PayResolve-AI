package com.payresolve.common;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback MessageConverter that passes every formatted message through
 * {@link SensitiveDataMasker#mask(String)} to prevent leaking datasource credentials.
 */
public class MaskingMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        return SensitiveDataMasker.mask(message);
    }
}

