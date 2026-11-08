package com.payresolve.common;

/**
 * Thrown when domain or API input fails validation rules.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}

