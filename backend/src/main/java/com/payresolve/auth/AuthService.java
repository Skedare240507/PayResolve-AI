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

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing user authentication, role-based security, BCrypt credential hashing,
 * viewer self-registration, and demo password reset links.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final AuditService auditService;
    private final AuthProperties authProperties;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AuthService(
        UserRepository userRepository,
        PasswordResetTokenRepository resetTokenRepository,
        AuditService auditService,
        AuthProperties authProperties,
        PasswordEncoder passwordEncoder,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.auditService = auditService;
        this.authProperties = authProperties;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
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
     * Authenticates credentials, creates a session, and attaches the SecurityContext.
     */
    public UserDto login(String username, String password, HttpServletRequest request) {
        UserRecord user = userRepository.findByUsername(username)
            .or(() -> userRepository.findByEmail(username))
            .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String roleAuthority = "ROLE_" + user.role().toUpperCase();
        var authorities = List.of(new SimpleGrantedAuthority(roleAuthority));
        var auth = new UsernamePasswordAuthenticationToken(user.username(), null, authorities);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

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
     * Creates a temporary password reset token.
     * In demo mode, returns the token and link directly on screen since no live email service is configured.
     */
    @Transactional
    public ForgotPasswordResponse requestPasswordReset(String identifier) {
        UserRecord user = userRepository.findByUsername(identifier)
            .or(() -> userRepository.findByEmail(identifier))
            .orElseThrow(() -> new ResourceNotFoundException("User not found with identifier: " + identifier));

        String token = UUID.randomUUID().toString().replace("-", "");
        Instant now = clock.instant();
        Instant expiresAt = now.plus(15, ChronoUnit.MINUTES);

        PasswordResetTokenRecord tokenRecord = new PasswordResetTokenRecord(
            null,
            user.id(),
            token,
            expiresAt,
            null,
            now
        );

        resetTokenRepository.insert(tokenRecord);
        log.info("Generated password reset token for user {}: token={}", user.username(), token);

        String resetUrl = "/reset-password?token=" + token;
        String notice = "Demo mode notice: No external email delivery service is configured. You can use the link or token displayed here to reset your password.";

        return new ForgotPasswordResponse(
            "Password reset link generated successfully.",
            token,
            resetUrl,
            notice
        );
    }

    /**
     * Resets password using an unexpired, unused token.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetTokenRecord tokenRecord = resetTokenRepository.findByToken(token)
            .orElseThrow(() -> new IllegalArgumentException("Invalid password reset token"));

        if (tokenRecord.usedAt() != null) {
            throw new IllegalArgumentException("This password reset token has already been used");
        }

        if (tokenRecord.expiresAt().isBefore(clock.instant())) {
            throw new IllegalArgumentException("Password reset token has expired. Please request a new one");
        }

        String newHash = passwordEncoder.encode(newPassword);
        userRepository.updatePassword(tokenRecord.userId(), newHash);
        resetTokenRepository.markUsed(tokenRecord.id(), clock.instant());

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
            session.invalidate();
        }
    }
}

