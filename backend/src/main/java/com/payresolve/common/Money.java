package com.payresolve.common;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Money value object representing monetary amounts in minor units (e.g., cents, pence)
 * alongside an ISO 4217 three-letter currency code.
 *
 * Invariants:
 * - Amount is always represented as integer minor units (long).
 * - No float, double, or raw BigDecimal in domain logic.
 * - Arithmetic or comparison across different currencies throws IllegalArgumentException.
 */
public record Money(long amountMinor, String currency) implements Serializable, Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "Currency code must not be null");
        String trimmedCurrency = currency.trim().toUpperCase();
        if (trimmedCurrency.length() != 3) {
            throw new IllegalArgumentException("Currency must be a 3-letter ISO 4217 code: " + currency);
        }
        currency = trimmedCurrency;
    }

    public static Money of(long amountMinor, String currency) {
        return new Money(amountMinor, currency);
    }

    public static Money zero(String currency) {
        return new Money(0L, currency);
    }

    /**
     * Parses major-unit string (e.g. "84.50" or "100") into Money minor units without floating-point drift.
     */
    public static Money parse(String majorAmount, String currency) {
        Objects.requireNonNull(majorAmount, "Amount string must not be null");
        Currency c = Currency.getInstance(currency.trim().toUpperCase());
        int defaultFractionDigits = Math.max(0, c.getDefaultFractionDigits());
        BigDecimal bd = new BigDecimal(majorAmount.trim());
        long minor = bd.movePointRight(defaultFractionDigits).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        return new Money(minor, c.getCurrencyCode());
    }

    public Money add(Money other) {
        ensureSameCurrency(other);
        return new Money(Math.addExact(this.amountMinor, other.amountMinor), this.currency);
    }

    public Money subtract(Money other) {
        ensureSameCurrency(other);
        return new Money(Math.subtractExact(this.amountMinor, other.amountMinor), this.currency);
    }

    public Money difference(Money other) {
        ensureSameCurrency(other);
        return new Money(Math.abs(this.amountMinor - other.amountMinor), this.currency);
    }

    public boolean isZero() {
        return this.amountMinor == 0L;
    }

    public boolean isPositive() {
        return this.amountMinor > 0L;
    }

    public boolean isNegative() {
        return this.amountMinor < 0L;
    }

    /**
     * Converts minor units to a double major unit amount solely for presentation/display purposes.
     */
    public double asMajorUnits() {
        try {
            Currency c = Currency.getInstance(this.currency);
            int fractionDigits = Math.max(0, c.getDefaultFractionDigits());
            return this.amountMinor / Math.pow(10, fractionDigits);
        } catch (Exception e) {
            return this.amountMinor / 100.0;
        }
    }

    public void ensureSameCurrency(Money other) {
        Objects.requireNonNull(other, "Other money must not be null");
        if (!this.currency.equalsIgnoreCase(other.currency)) {
            throw new IllegalArgumentException(
                "Cannot perform operation on different currencies: " + this.currency + " vs " + other.currency
            );
        }
    }

    @Override
    public int compareTo(Money other) {
        ensureSameCurrency(other);
        return Long.compare(this.amountMinor, other.amountMinor);
    }

    /**
     * Standard human-readable string formatter, e.g. "84.50 USD".
     */
    public String format() {
        try {
            Currency c = Currency.getInstance(this.currency);
            int fractionDigits = Math.max(0, c.getDefaultFractionDigits());
            if (fractionDigits == 0) {
                return this.amountMinor + " " + this.currency;
            }
            long factor = (long) Math.pow(10, fractionDigits);
            long major = this.amountMinor / factor;
            long minorPart = Math.abs(this.amountMinor % factor);
            String minorStr = String.format("%0" + fractionDigits + "d", minorPart);
            return String.format("%s%d.%s %s", (this.amountMinor < 0 && major == 0 ? "-" : ""), major, minorStr, this.currency);
        } catch (Exception e) {
            return (this.amountMinor / 100.0) + " " + this.currency;
        }
    }

    @Override
    public String toString() {
        return format();
    }
}

