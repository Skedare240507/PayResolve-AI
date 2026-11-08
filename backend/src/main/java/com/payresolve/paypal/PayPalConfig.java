package com.payresolve.paypal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Spring configuration for PayPal module components and RestClient.
 */
@Configuration
@EnableConfigurationProperties(PayPalProperties.class)
public class PayPalConfig {

    @Bean
    public RestClient payPalRestClient(PayPalProperties properties, PayPalAuthService authService) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
            .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));

        return RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(new BufferingClientHttpRequestFactory(factory))
            .requestInterceptor(new PayPalRequestInterceptor(authService))
            .build();
    }
}
