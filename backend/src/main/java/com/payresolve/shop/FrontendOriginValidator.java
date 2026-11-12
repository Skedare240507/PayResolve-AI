package com.payresolve.shop;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Fails fast at startup if FRONTEND_ORIGIN is missing or remains localhost while APP_PUBLIC_URL
 * uses HTTPS (which would break PayPal Checkout return_url/cancel_url redirects in production).
 */
@Component
public class FrontendOriginValidator {

    private final String publicUrl;
    private final String frontendOrigin;

    public FrontendOriginValidator(
        @Value("${payresolve.public-url:http://localhost:8080}") String publicUrl,
        @Value("${payresolve.frontend-origin:}") String frontendOrigin
    ) {
        this.publicUrl = publicUrl;
        this.frontendOrigin = frontendOrigin;
    }

    @PostConstruct
    public void validate() {
        validate(publicUrl, frontendOrigin);
    }

    public static void validate(String publicUrl, String frontendOrigin) {
        if (frontendOrigin == null || frontendOrigin.isBlank()) {
            throw new IllegalStateException(
                "FRONTEND_ORIGIN must be configured and non-blank (e.g. https://payresolve-ui.onrender.com or http://localhost:5173)."
            );
        }
        String normPublic = publicUrl == null ? "" : publicUrl.trim().toLowerCase(Locale.ROOT);
        String normFrontend = frontendOrigin.trim().toLowerCase(Locale.ROOT);
        boolean isLocalhost = normFrontend.startsWith("http://localhost")
            || normFrontend.startsWith("https://localhost")
            || normFrontend.startsWith("http://127.0.0.1")
            || normFrontend.startsWith("https://127.0.0.1");

        if (normPublic.startsWith("https://") && isLocalhost) {
            throw new IllegalStateException(
                "FRONTEND_ORIGIN cannot remain localhost (" + frontendOrigin + ") while APP_PUBLIC_URL is https ("
                    + publicUrl + "). Set FRONTEND_ORIGIN to your deployed HTTPS UI URL."
            );
        }
    }
}

