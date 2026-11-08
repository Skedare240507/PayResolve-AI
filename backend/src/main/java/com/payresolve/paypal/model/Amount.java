package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.payresolve.common.Money;

import java.util.Currency;
import java.util.Objects;

/**
 * Standard PayPal amount representation consisting of an ISO 4217 currency code
 * and decimal string value.
 */
public record Amount(
    @JsonProperty("currency_code") String currencyCode,
    @JsonProperty("value") String value
) {
    public Amount {
        Objects.requireNonNull(currencyCode, "Currency code must not be null");
        Objects.requireNonNull(value, "Amount value must not be null");
    }

    /**
     * Converts a domain Money object to a PayPal Amount representation.
     */
    public static Amount fromMoney(Money money) {
        Objects.requireNonNull(money, "Money must not be null");
        String currencyCode = money.currency();
        Currency c = Currency.getInstance(currencyCode);
        int fractionDigits = Math.max(0, c.getDefaultFractionDigits());
        long factor = (long) Math.pow(10, fractionDigits);
        long major = money.amountMinor() / factor;
        long minorPart = Math.abs(money.amountMinor() % factor);
        String formattedValue;
        if (fractionDigits == 0) {
            formattedValue = String.valueOf(money.amountMinor());
        } else {
            String minorStr = String.format("%0" + fractionDigits + "d", minorPart);
            formattedValue = String.format("%s%d.%s", (money.amountMinor() < 0 && major == 0 ? "-" : ""), major, minorStr);
        }
        return new Amount(currencyCode, formattedValue);
    }

    /**
     * Converts this PayPal Amount representation to a domain Money object.
     */
    public Money toMoney() {
        return Money.parse(this.value, this.currencyCode);
    }
}
