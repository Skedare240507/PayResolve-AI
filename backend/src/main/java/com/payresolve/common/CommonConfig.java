package com.payresolve.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Common application configuration providing shared infrastructure beans.
 */
@Configuration
public class CommonConfig {

    /**
     * Injected Clock bean allowing deterministic time manipulation during automated tests.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

