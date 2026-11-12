package com.payresolve.common;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Security configuration establishing:
 * 1. BCrypt password hashing bean
 * 2. Cookie-based CSRF protection across all routes except /api/webhooks/**
 * 3. Role-based authorization matrix (public endpoints, VIEWER/OPERATOR reads + AI chat/investigate, OPERATOR writes)
 * 4. Exact-origin CORS configuration for the frontend origin
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final String allowedOrigins;
    private final ObjectMapper objectMapper;
    private final RateLimitService rateLimitService;

    public SecurityConfig(
        @Value("${payresolve.cors.allowed-origins:http://localhost:5173}") String allowedOrigins,
        ObjectMapper objectMapper,
        RateLimitService rateLimitService
    ) {
        this.allowedOrigins = allowedOrigins;
        this.objectMapper = objectMapper;
        this.rateLimitService = rateLimitService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepository)
                .csrfTokenRequestHandler(csrfRequestHandler)
                .ignoringRequestMatchers("/api/webhooks/**")
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler(problemDetailAccessDeniedHandler())
            )
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers(HttpMethod.POST, "/api/webhooks/paypal").permitAll()
                .requestMatchers(HttpMethod.POST,
                    "/api/auth/login",
                    "/api/auth/signup",
                    "/api/auth/forgot-password",
                    "/api/auth/reset-password"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/shop/products").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/shop/orders/*/capture").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/shop/orders/*/status").permitAll()

                // OpenAPI / Swagger UI & other actuator endpoints: OPERATOR only
                .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").hasRole("OPERATOR")
                .requestMatchers("/actuator/**").hasRole("OPERATOR")

                // Explicit OPERATOR-only GET endpoints
                .requestMatchers(HttpMethod.GET, "/api/paypal/diagnostics").hasRole("OPERATOR")

                // Signed-in (VIEWER or OPERATOR): AI investigation / chat, logout, and all remaining GETs
                .requestMatchers(HttpMethod.POST, "/api/cases/*/investigate", "/api/agent/chat", "/api/auth/logout").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/**").authenticated()

                // OPERATOR only: every other POST, PUT, PATCH, DELETE
                .requestMatchers(HttpMethod.POST, "/**").hasRole("OPERATOR")
                .requestMatchers(HttpMethod.PUT, "/**").hasRole("OPERATOR")
                .requestMatchers(HttpMethod.PATCH, "/**").hasRole("OPERATOR")
                .requestMatchers(HttpMethod.DELETE, "/**").hasRole("OPERATOR")
                .anyRequest().authenticated()
            )
            .addFilterAfter(new RateAndPayloadFilter(rateLimitService, objectMapper), AuthorizationFilter.class);

        return http.build();
    }

    private AccessDeniedHandler problemDetailAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

            boolean csrfFailure = accessDeniedException instanceof CsrfException;
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                csrfFailure ? "Invalid or missing CSRF token." : "Forbidden: insufficient permissions."
            );
            problem.setTitle("Forbidden");
            problem.setType(URI.create(csrfFailure ? "urn:payresolve:csrf-invalid" : "urn:payresolve:forbidden"));
            problem.setProperty("timestamp", Instant.now());
            response.getWriter().write(objectMapper.writeValueAsString(problem));
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty() && !"*".equals(s))
            .forEach(configuration::addAllowedOrigin);

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
