package com.payresolve.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    @DisplayName("Should create Money with minor units and currency code")
    void shouldCreateMoney() {
        Money money = Money.of(8450L, "USD");
        assertThat(money.amountMinor()).isEqualTo(8450L);
        assertThat(money.currency()).isEqualTo("USD");
        assertThat(money.format()).isEqualTo("84.50 USD");
    }

    @Test
    @DisplayName("Should parse decimal string amount into integer minor units")
    void shouldParseMajorAmountString() {
        Money money = Money.parse("84.50", "USD");
        assertThat(money.amountMinor()).isEqualTo(8450L);
        assertThat(money.currency()).isEqualTo("USD");

        Money wholeAmount = Money.parse("100", "USD");
        assertThat(wholeAmount.amountMinor()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("Should perform arithmetic on identical currencies")
    void shouldAddAndSubtractSameCurrency() {
        Money m1 = Money.of(5000L, "USD");
        Money m2 = Money.of(3450L, "USD");

        Money sum = m1.add(m2);
        assertThat(sum.amountMinor()).isEqualTo(8450L);

        Money diff = m1.subtract(m2);
        assertThat(diff.amountMinor()).isEqualTo(1550L);
    }

    @Test
    @DisplayName("Should reject arithmetic or comparison across different currencies")
    void shouldRejectCrossCurrencyOperations() {
        Money usd = Money.of(5000L, "USD");
        Money eur = Money.of(5000L, "EUR");

        assertThatThrownBy(() -> usd.add(eur))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot perform operation on different currencies");

        assertThatThrownBy(() -> usd.compareTo(eur))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should validate currency code format")
    void shouldValidateCurrencyCode() {
        assertThatThrownBy(() -> Money.of(1000L, "US"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3-letter ISO 4217 code");

        assertThatThrownBy(() -> Money.of(1000L, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should check zero, positive, and negative amounts")
    void shouldCheckSign() {
        assertThat(Money.zero("USD").isZero()).isTrue();
        assertThat(Money.of(100L, "USD").isPositive()).isTrue();
        assertThat(Money.of(-100L, "USD").isNegative()).isTrue();
    }
}

