package com.payresolve.ai;

import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * Shared HTTP client configuration, API key resolution, transient retry-once logic,
 * and secret-safe error classification for LLM provider clients.
 */
final class LlmHttpSupport {

    static final long DEFAULT_CONNECT_TIMEOUT_MS = 10_000L;
    static final long DEFAULT_READ_TIMEOUT_MS = 35_000L;

    private LlmHttpSupport() {}

    static RestClient buildRestClient(String baseUrl, long connectTimeoutMs, long readTimeoutMs) {
        long connect = connectTimeoutMs > 0 ? connectTimeoutMs : DEFAULT_CONNECT_TIMEOUT_MS;
        long read = readTimeoutMs > 0 ? readTimeoutMs : DEFAULT_READ_TIMEOUT_MS;

        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connect))
            .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(read));

        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(new BufferingClientHttpRequestFactory(factory))
            .build();
    }

    static String resolveApiKey(String configuredKey, String envVarName) {
        if (isValidKey(configuredKey)) {
            return configuredKey.trim();
        }
        String sysEnv = System.getenv(envVarName);
        if (isValidKey(sysEnv)) {
            return sysEnv.trim();
        }
        return readFromEnvFile(envVarName);
    }

    static boolean isValidKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String lower = key.toLowerCase();
        return !lower.startsWith("mock_")
            && !lower.contains("placeholder")
            && !lower.contains("your_")
            && !lower.contains("youractual")
            && !lower.contains("your-actual")
            && key.length() > 15;
    }

    static <T> T executeWithTransientRetry(Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException firstFailure) {
            if (!isTransient(firstFailure)) {
                throw firstFailure;
            }
            return action.get();
        }
    }

    static boolean isTransient(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof ResourceAccessException
                || cursor instanceof SocketTimeoutException
                || cursor instanceof HttpTimeoutException) {
                return true;
            }
            if (cursor instanceof RestClientResponseException rce) {
                int code = rce.getStatusCode().value();
                return code == 429 || code == 408 || code >= 500;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    /**
     * Produces a concise, secret-safe error class string for UI badges and database telemetry.
     */
    static String classifyError(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof RestClientResponseException rce) {
                int code = rce.getStatusCode().value();
                return switch (code) {
                    case 400 -> "HTTP 400 Bad Request";
                    case 401 -> "HTTP 401 Unauthorized";
                    case 403 -> "HTTP 403 Forbidden";
                    case 404 -> "HTTP 404 Model Not Found";
                    case 429 -> "HTTP 429 Rate Limited";
                    default -> code >= 500 ? "HTTP " + code + " Server Error" : "HTTP " + code;
                };
            }
            if (cursor instanceof SocketTimeoutException || cursor instanceof HttpTimeoutException) {
                return "Timeout";
            }
            if (cursor instanceof ResourceAccessException) {
                return "Network unreachable";
            }
            cursor = cursor.getCause();
        }
        String msg = throwable.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("not configured")) return "Not configured";
            if (lower.contains("timeout") || lower.contains("timed out")) return "Timeout";
            if (lower.contains("401")) return "HTTP 401 Unauthorized";
            if (lower.contains("403")) return "HTTP 403 Forbidden";
            if (lower.contains("404")) return "HTTP 404 Model Not Found";
            if (lower.contains("429")) return "HTTP 429 Rate Limited";
            if (lower.contains("500") || lower.contains("502") || lower.contains("503") || lower.contains("504")) {
                return "HTTP 5xx Server Error";
            }
        }
        return throwable.getClass().getSimpleName();
    }

    private static String readFromEnvFile(String varName) {
        for (String candidate : List.of(".env", "../.env")) {
            Path p = Path.of(candidate);
            if (Files.exists(p)) {
                try {
                    for (String line : Files.readAllLines(p)) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith(varName + "=")) {
                            String val = trimmed.substring(varName.length() + 1).trim();
                            if ((val.startsWith("\"") && val.endsWith("\""))
                                || (val.startsWith("'") && val.endsWith("'"))) {
                                val = val.substring(1, val.length() - 1);
                            }
                            if (isValidKey(val)) {
                                return val;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return "";
    }
}

