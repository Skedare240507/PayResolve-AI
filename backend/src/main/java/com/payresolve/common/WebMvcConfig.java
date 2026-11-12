package com.payresolve.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Web MVC configuration establishing /api prefix for all RestControllers
 * and Cross-Origin Resource Sharing (CORS) rules.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final String allowedOrigins;

    public WebMvcConfig(@Value("${payresolve.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Enforce that all PayResolve application @RestController endpoints live under /api
        // and do not alter springdoc OpenAPI endpoints (/v3/api-docs)
        configurer.addPathPrefix("/api",
            HandlerTypePredicate.forAnnotation(RestController.class)
                .and(HandlerTypePredicate.forBasePackage("com.payresolve")));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !"*".equals(s))
                .toArray(String[]::new);

        registry.addMapping("/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
