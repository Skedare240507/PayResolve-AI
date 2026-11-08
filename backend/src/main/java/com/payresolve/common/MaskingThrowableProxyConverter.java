package com.payresolve.common;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback ThrowableProxyConverter that intercepts formatted exception stack traces
 * and passes them through {@link SensitiveDataMasker#mask(String)} to redact database
 * URLs, credentials, and connection error details.
 */
public class MaskingThrowableProxyConverter extends ThrowableProxyConverter {

    @Override
    public String convert(ILoggingEvent event) {
        String result = super.convert(event);
        return SensitiveDataMasker.mask(result);
    }
}
