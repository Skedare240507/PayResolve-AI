package com.payresolve.paypal;

import com.payresolve.common.PayResolveException;

/**
 * Exception thrown when a PayPal REST API call fails with an HTTP error.
 */
public class PayPalApiException extends PayResolveException {

    private final int statusCode;
    private final String responseBody;

    public PayPalApiException(int statusCode, String message, String responseBody) {
        super(String.format("PayPal API error [%d]: %s", statusCode, message));
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public PayPalApiException(int statusCode, String message, String responseBody, Throwable cause) {
        super(String.format("PayPal API error [%d]: %s", statusCode, message), cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
