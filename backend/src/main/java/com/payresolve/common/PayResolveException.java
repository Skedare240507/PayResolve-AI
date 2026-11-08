package com.payresolve.common;

/**
 * Base unchecked exception for PayResolve business logic errors.
 */
public class PayResolveException extends RuntimeException {

    public PayResolveException(String message) {
        super(message);
    }

    public PayResolveException(String message, Throwable cause) {
        super(message, cause);
    }
}

