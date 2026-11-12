package com.payresolve.shop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DemoFaultSwitchesTest {

    private static final class MutableClock extends Clock {
        private Instant current;

        MutableClock(Instant initial) {
            this.current = initial;
        }

        void advanceSeconds(long seconds) {
            this.current = this.current.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    @Test
    @DisplayName("Enabled fault switches auto-reset to off 10 minutes after being turned on")
    void faultSwitchesAutoResetAfterTenMinutes() {
        Instant start = Instant.parse("2026-11-12T10:00:00Z");
        MutableClock clock = new MutableClock(start);
        DemoFaultSwitches switches = new DemoFaultSwitches(mock(JdbcClient.class), clock, true);

        switches.setFailWebhookHandler(true);
        assertThat(switches.isFailWebhookHandler()).isTrue();
        assertThat(switches.getResetsAt()).isEqualTo(start.plusSeconds(600));
        assertThat(switches.getActiveFaultNames()).containsExactly("Fail webhook handler");

        // Still active at 9m 59s
        clock.advanceSeconds(599);
        assertThat(switches.isFailWebhookHandler()).isTrue();

        // Auto-resets at 10m 00s
        clock.advanceSeconds(1);
        assertThat(switches.isFailWebhookHandler()).isFalse();
        assertThat(switches.anyActive()).isFalse();
        assertThat(switches.getResetsAt()).isNull();
        assertThat(switches.getActiveFaultNames()).isEmpty();
    }
}
