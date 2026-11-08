package com.payresolve.common;

/**
 * Thrown when a requested resource (case, order, event) does not exist.
 * Mapped to RFC 9457 HTTP 404 NOT_FOUND.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
