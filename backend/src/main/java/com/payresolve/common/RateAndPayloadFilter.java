package com.payresolve.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Enforces endpoint-specific rate limits (429 + Retry-After) and webhook request payload size
 * limits (256 KB max on POST /api/webhooks/paypal, returning 413 Payload Too Large).
 * Relies on {@code request.getRemoteAddr()}, which Tomcat's native forwarded-header handling
 * populates from proxy headers only when received from trusted internal proxies (e.g. Render).
 */
public class RateAndPayloadFilter extends OncePerRequestFilter {

    public static final int MAX_WEBHOOK_BYTES = 256 * 1024;

    private static final Pattern INVESTIGATE_PATH = Pattern.compile("^/api/cases/[^/]+/investigate$");
    private static final Pattern ATTACHMENT_UPLOAD_PATH = Pattern.compile("^/api/cases/[^/]+/attachments$");

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public RateAndPayloadFilter(RateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String method = request.getMethod();
        String path = resolveApplicationPath(request);
        String clientIp = request.getRemoteAddr();

        // 1. Enforce 256 KB body size limit on PayPal webhooks
        if (HttpMethod.POST.matches(method) && "/api/webhooks/paypal".equals(path)) {
            long declaredLength = request.getContentLengthLong();
            if (declaredLength > MAX_WEBHOOK_BYTES) {
                writeProblem(
                    response,
                    HttpStatus.CONTENT_TOO_LARGE,
                    "Payload Too Large",
                    "urn:payresolve:payload-too-large",
                    "Webhook payload exceeds the 256 KB limit.",
                    null
                );
                return;
            }
            byte[] buffered = readUpToLimit(request.getInputStream(), MAX_WEBHOOK_BYTES);
            if (buffered == null) {
                writeProblem(
                    response,
                    HttpStatus.CONTENT_TOO_LARGE,
                    "Payload Too Large",
                    "urn:payresolve:payload-too-large",
                    "Webhook payload exceeds the 256 KB limit.",
                    null
                );
                return;
            }
            request = new CachedBodyRequestWrapper(request, buffered);
        }

        // 2. Enforce rate limits by IP or authenticated user
        RateLimitService.RateLimitDecision decision = checkRateLimit(method, path, clientIp);
        if (decision != null && !decision.allowed()) {
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            writeProblem(
                response,
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                "urn:payresolve:rate-limited",
                "Rate limit exceeded. Try again in " + decision.retryAfterSeconds() + " seconds.",
                decision.retryAfterSeconds()
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RateLimitService.RateLimitDecision checkRateLimit(String method, String path, String clientIp) {
        if (!HttpMethod.POST.matches(method)) {
            return null;
        }

        if ("/api/auth/login".equals(path)) {
            return rateLimitService.checkAndConsume("login:ip:" + clientIp, 10, Duration.ofMinutes(10));
        }
        if ("/api/auth/signup".equals(path)) {
            return rateLimitService.checkAndConsume("signup:ip:" + clientIp, 5, Duration.ofHours(1));
        }
        if ("/api/auth/forgot-password".equals(path)) {
            return rateLimitService.checkAndConsume("forgot-password:ip:" + clientIp, 5, Duration.ofHours(1));
        }

        String username = currentAuthenticatedUsername();
        if (username != null) {
            if ("/api/agent/chat".equals(path)) {
                return rateLimitService.checkAndConsume("agent-chat:user:" + username, 20, Duration.ofHours(1));
            }
            if (INVESTIGATE_PATH.matcher(path).matches()) {
                return rateLimitService.checkAndConsume("investigate:user:" + username, 10, Duration.ofHours(1));
            }
            if (ATTACHMENT_UPLOAD_PATH.matcher(path).matches()) {
                return rateLimitService.checkAndConsume("attachment-upload:user:" + username, 10, Duration.ofHours(1));
            }
        }
        return null;
    }

    private String resolveApplicationPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private String currentAuthenticatedUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getName();
    }

    private byte[] readUpToLimit(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 16 * 1024));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                return null;
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private void writeProblem(
        HttpServletResponse response,
        HttpStatus status,
        String title,
        String typeUri,
        String detail,
        Long retryAfterSeconds
    ) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(typeUri));
        problem.setProperty("timestamp", Instant.now());
        if (retryAfterSeconds != null) {
            problem.setProperty("retryAfterSeconds", retryAfterSeconds);
        }
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }

    private static final class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return bais.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // Synchronous stream backed by in-memory byte array
                }

                @Override
                public int read() {
                    return bais.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return bais.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding() != null ? getCharacterEncoding() : StandardCharsets.UTF_8.name();
            return new BufferedReader(new InputStreamReader(getInputStream(), java.nio.charset.Charset.forName(encoding)));
        }
    }
}

