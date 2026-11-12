package com.payresolve.auth;

import com.payresolve.audit.AuditService;
import com.payresolve.auth.dto.ForgotPasswordResponse;
import com.payresolve.auth.dto.SignUpRequest;
import com.payresolve.auth.dto.UserDto;
import com.payresolve.common.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service managing user authentication, role-based security, BCrypt credential hashing,
 * viewer self-registration, and demo password reset links.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    public static final String GENERIC_RESET_MESSAGE = "If that account exists, ask the administrator to reset it.";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final AuditService auditService;
    private final AuthProperties authProperties;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final ActiveSessionRegistry activeSessionRegistry;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(
        UserRepository userRepository,
        PasswordResetTokenRepository resetTokenRepository,
        AuditService auditService,
        AuthProperties authProperties,
        PasswordEncoder passwordEncoder,
        LoginAttemptService loginAttemptService,
        ActiveSessionRegistry activeSessionRegistry,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.auditService = auditService;
        this.authProperties = authProperties;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.activeSessionRegistry = activeSessionRegistry;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("payresolve-dummy-timing-guard");
    }

    /**
     * Seeds default operator and viewer accounts from environment variables / configuration.
     */
    @PostConstruct
    public void seedDefaultUsers() {
        // 1. Seed OPERATOR
        String opUser = authProperties.getOperatorUsername();
        if (userRepository.findByUsername(opUser).isEmpty()) {
            String opHash = passwordEncoder.encode(authProperties.getOperatorPassword());
            userRepository.insert(new UserRecord(
                null,
                opUser,
                opUser + "@payresolve.local",
                opHash,
                "OPERATOR",
                clock.instant(),
                clock.instant()
            ));
            log.info("Seeded default OPERATOR account: username='{}'", opUser);
        }

        // 2. Seed VIEWER
        String viewUser = authProperties.getViewerUsername();
        if (userRepository.findByUsername(viewUser).isEmpty()) {
            String viewHash = passwordEncoder.encode(authProperties.getViewerPassword());
            userRepository.insert(new UserRecord(
                null,
                viewUser,
                viewUser + "@payresolve.local",
                viewHash,
                "VIEWER",
                clock.instant(),
                clock.instant()
            ));
            log.info("Seeded default VIEWER account: username='{}'", viewUser);
        }
    }

    /**
     * Self-registration: creates a VIEWER account only.
     */
    @Transactional
    public UserDto signUp(SignUpRequest request) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new IllegalArgumentException("Username is already taken: " + request.username());
        }
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalArgumentException("Email is already registered: " + request.email());
        }

        String passwordHash = passwordEncoder.encode(request.password());
        Instant now = clock.instant();

        UserRecord user = new UserRecord(
            null,
            request.username().trim(),
            request.email().trim(),
            passwordHash,
            "VIEWER", // Restricted role: sign-up creates a VIEWER only
            now,
            now
        );

        Long userId = userRepository.insert(user);
        log.info("Registered new VIEWER account id={} username='{}'", userId, user.username());

        auditService.record(
            user.username(),
            "USER_SIGNUP",
            "USER",
            userId.toString(),
            Map.of("role", "VIEWER", "email", user.email())
        );

        return new UserDto(userId, user.username(), user.email(), "VIEWER", true);
    }

    /**
     * Authenticates credentials, enforces per-username 5-failure lockout (15m) with uniform timing
     * and error message, creates a session, and attaches the SecurityContext.
     */
    public UserDto login(String username, String password, HttpServletRequest request) {
        String identifier = username == null ? "" : username.trim();
        Optional<UserRecord> userOpt = userRepository.findByUsername(identifier)
            .or(() -> userRepository.findByEmail(identifier));

        String lockoutKey = userOpt.map(UserRecord::username).orElse(identifier);

        // Enforce 15-minute lockout with uniform BCrypt work & uniform error message
        if (loginAttemptService.isLocked(identifier) || loginAttemptService.isLocked(lockoutKey)) {
            passwordEncoder.matches(password != null ? password : "", dummyPasswordHash);
            throw new BadCredentialsException("Invalid username or password");
        }

        if (userOpt.isEmpty()) {
            passwordEncoder.matches(password != null ? password : "", dummyPasswordHash);
            loginAttemptService.recordFailure(identifier);
            throw new BadCredentialsException("Invalid username or password");
        }

        UserRecord user = userOpt.get();
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            loginAttemptService.recordFailure(user.username());
            if (!identifier.equalsIgnoreCase(user.username())) {
                loginAttemptService.recordFailure(identifier);
            }
            throw new BadCredentialsException("Invalid username or password");
        }

        loginAttemptService.clearFailures(user.username());
        loginAttemptService.clearFailures(identifier);

        String roleAuthority = "ROLE_" + user.role().toUpperCase();
        var authorities = List.of(new SimpleGrantedAuthority(roleAuthority));
        var auth = new UsernamePasswordAuthenticationToken(user.username(), null, authorities);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        activeSessionRegistry.registerSession(user.username(), session);

        auditService.record(
            user.username(),
            "USER_LOGIN",
            "USER",
            user.id().toString(),
            Map.of("role", user.role())
        );

        return UserDto.fromRecord(user);
    }

    /**
     * Initiates a password reset without revealing whether an account exists.
     * Only stores a SHA-256 hash of the token (15-minute expiry, invalidating prior tokens)
     * and never logs any token value. Only returns an on-screen reset link when
     * {@code payresolve.auth.demo-reset-links=true} AND the target account role is VIEWER.
     */
    @Transactional
    public ForgotPasswordResponse requestPasswordReset(String identifier) {
        String trimmed = identifier == null ? "" : identifier.trim();
        Optional<UserRecord> userOpt = trimmed.isEmpty()
            ? Optional.empty()
            : userRepository.findByUsername(trimmed).or(() -> userRepository.findByEmail(trimmed));

        if (userOpt.isEmpty()) {
            return new ForgotPasswordResponse(GENERIC_RESET_MESSAGE, null, null, null);
        }

        UserRecord user = userOpt.get();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(15, ChronoUnit.MINUTES);

        resetTokenRepository.invalidateActiveTokensForUser(user.id(), now);

        String rawToken = generateSecureToken();
        String tokenHash = sha256Hex(rawToken);

        PasswordResetTokenRecord tokenRecord = new PasswordResetTokenRecord(
            null,
            user.id(),
            tokenHash,
            expiresAt,
            null,
            now
        );
        resetTokenRepository.insert(tokenRecord);
        log.info("Issued password reset token for userId={} (expiresAt={})", user.id(), expiresAt);

        if (authProperties.isDemoResetLinks() && "VIEWER".equalsIgnoreCase(user.role())) {
            String resetUrl = "/reset-password?token=" + rawToken;
            String notice = "Demo reset link (VIEWER only, valid for 15 minutes): no external email service is configured.";
            return new ForgotPasswordResponse(GENERIC_RESET_MESSAGE, rawToken, resetUrl, notice);
        }

        return new ForgotPasswordResponse(GENERIC_RESET_MESSAGE, null, null, null);
    }

    /**
     * Resets password using an unexpired, unused token (looked up by SHA-256 hash)
     * and signs out all existing sessions for that user.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Invalid password reset token");
        }

        String tokenHash = sha256Hex(token.trim());
        PasswordResetTokenRecord tokenRecord = resetTokenRepository.findByToken(tokenHash)
            .orElseThrow(() -> new IllegalArgumentException("Invalid password reset token"));

        if (tokenRecord.usedAt() != null) {
            throw new IllegalArgumentException("This password reset token has already been used");
        }

        Instant now = clock.instant();
        if (!tokenRecord.expiresAt().isAfter(now)) {
            throw new IllegalArgumentException("Password reset token has expired. Please request a new one");
        }

        String newHash = passwordEncoder.encode(newPassword);
        userRepository.updatePassword(tokenRecord.userId(), newHash);
        resetTokenRepository.markUsed(tokenRecord.id(), now);

        userRepository.findById(tokenRecord.userId()).ifPresent(user -> {
            loginAttemptService.clearFailures(user.username());
            activeSessionRegistry.invalidateSessionsForUser(user.username());
        });

        auditService.record(
            "system",
            "PASSWORD_RESET",
            "USER",
            tokenRecord.userId().toString(),
            Map.of("tokenId", tokenRecord.id())
        );
    }

    /**
     * Returns current authenticated user or anonymous.
     */
    public UserDto getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return UserDto.anonymous();
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username)
            .map(UserDto::fromRecord)
            .orElse(new UserDto(null, username, "", "VIEWER", true));
    }

    /**
     * Logs out the user and clears the session.
     */
    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            activeSessionRegistry.unregisterSession(session);
            session.invalidate();
        }
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

