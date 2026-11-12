package com.payresolve.auth;

import com.payresolve.audit.AuditService;
import com.payresolve.auth.dto.ForgotPasswordResponse;
import com.payresolve.auth.dto.SignUpRequest;
import com.payresolve.auth.dto.UserDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository resetTokenRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private HttpSession httpSession;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC);
    private final AuthProperties authProperties = new AuthProperties();
    private final ActiveSessionRegistry activeSessionRegistry = new ActiveSessionRegistry();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            userRepository,
            resetTokenRepository,
            auditService,
            authProperties,
            passwordEncoder,
            new LoginAttemptService(clock),
            activeSessionRegistry,
            clock
        );
    }

    @Test
    @DisplayName("Should seed default operator and viewer accounts when absent")
    void shouldSeedDefaultAccounts() {
        when(userRepository.findByUsername("operator")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("viewer")).thenReturn(Optional.empty());

        authService.seedDefaultUsers();

        verify(userRepository, times(2)).insert(any());
    }

    @Test
    @DisplayName("Sign up creates an account with VIEWER role only")
    void shouldSignUpViewerOnly() {
        SignUpRequest request = new SignUpRequest("newuser", "newuser@example.com", "secret123");
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("newuser@example.com")).thenReturn(Optional.empty());
        when(userRepository.insert(any())).thenReturn(50L);

        UserDto user = authService.signUp(request);

        assertThat(user.id()).isEqualTo(50L);
        assertThat(user.username()).isEqualTo("newuser");
        assertThat(user.role()).isEqualTo("VIEWER"); // Strictly VIEWER

        verify(auditService).record(eq("newuser"), eq("USER_SIGNUP"), eq("USER"), eq("50"), any());
    }

    @Test
    @DisplayName("Forgot password by default hides account existence and returns no token/link for any role")
    void forgotPasswordDefaultHidesTokenAndAccountExistence() {
        UserRecord viewer = new UserRecord(
            2L, "viewer", "viewer@example.com", "hash", "VIEWER", clock.instant(), clock.instant()
        );
        when(userRepository.findByUsername("viewer")).thenReturn(Optional.of(viewer));
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("missing")).thenReturn(Optional.empty());

        ForgotPasswordResponse viewerRes = authService.requestPasswordReset("viewer");
        ForgotPasswordResponse missingRes = authService.requestPasswordReset("missing");

        assertThat(viewerRes.message()).isEqualTo(AuthService.GENERIC_RESET_MESSAGE);
        assertThat(viewerRes.resetToken()).isNull();
        assertThat(viewerRes.resetUrl()).isNull();
        assertThat(viewerRes.demoNotice()).isNull();
        assertThat(missingRes).isEqualTo(viewerRes);

        verify(resetTokenRepository).invalidateActiveTokensForUser(2L, clock.instant());
        verify(resetTokenRepository).insert(argThat(r -> r.tokenHash().length() == 64));
    }

    @Test
    @DisplayName("Demo reset links only returned when demoResetLinks=true AND target role is VIEWER (never OPERATOR)")
    void demoResetLinksViewerOnly() {
        authProperties.setDemoResetLinks(true);

        UserRecord viewer = new UserRecord(
            2L, "viewer", "viewer@example.com", "hash", "VIEWER", clock.instant(), clock.instant()
        );
        UserRecord operator = new UserRecord(
            1L, "operator", "operator@example.com", "hash", "OPERATOR", clock.instant(), clock.instant()
        );
        when(userRepository.findByUsername("viewer")).thenReturn(Optional.of(viewer));
        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(operator));

        ForgotPasswordResponse viewerRes = authService.requestPasswordReset("viewer");
        assertThat(viewerRes.resetToken()).isNotBlank();
        assertThat(viewerRes.resetUrl()).isEqualTo("/reset-password?token=" + viewerRes.resetToken());
        assertThat(viewerRes.demoNotice()).contains("VIEWER only");

        ForgotPasswordResponse opRes = authService.requestPasswordReset("operator");
        assertThat(opRes.message()).isEqualTo(AuthService.GENERIC_RESET_MESSAGE);
        assertThat(opRes.resetToken()).isNull();
        assertThat(opRes.resetUrl()).isNull();
        assertThat(opRes.demoNotice()).isNull();
    }

    @Test
    @DisplayName("Reset password hashes token to SHA-256, marks token used, and invalidates active user sessions")
    void shouldResetPassword() {
        String rawToken = "valid-token-123";
        String tokenHash = AuthService.sha256Hex(rawToken);
        PasswordResetTokenRecord tokenRecord = new PasswordResetTokenRecord(
            10L, 1L, tokenHash, clock.instant().plus(15, ChronoUnit.MINUTES), null, clock.instant()
        );
        UserRecord operator = new UserRecord(
            1L, "operator", "op@example.com", "oldHash", "OPERATOR", clock.instant(), clock.instant()
        );

        when(resetTokenRepository.findByToken(tokenHash)).thenReturn(Optional.of(tokenRecord));
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));

        HttpSession session = mock(HttpSession.class);
        activeSessionRegistry.registerSession("operator", session);

        authService.resetPassword(rawToken, "newPassword456");

        verify(userRepository).updatePassword(eq(1L), anyString());
        verify(resetTokenRepository).markUsed(eq(10L), eq(clock.instant()));
        verify(session).invalidate();
        verify(auditService).record(eq("system"), eq("PASSWORD_RESET"), eq("USER"), eq("1"), any());
    }

    @Test
    @DisplayName("Reset password rejects expired tokens after 15 minutes")
    void shouldRejectExpiredResetToken() {
        String rawToken = "expired-token";
        PasswordResetTokenRecord expired = new PasswordResetTokenRecord(
            11L, 1L, AuthService.sha256Hex(rawToken), clock.instant().minusSeconds(1), null, clock.instant().minus(16, ChronoUnit.MINUTES)
        );
        when(resetTokenRepository.findByToken(AuthService.sha256Hex(rawToken))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.resetPassword(rawToken, "newPassword456"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Login with valid password succeeds and establishes session")
    void shouldLoginSuccessfully() {
        String hashed = passwordEncoder.encode("correctPassword");
        UserRecord user = new UserRecord(
            1L, "operator", "op@example.com", hashed, "OPERATOR", clock.instant(), clock.instant()
        );

        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(user));
        when(httpRequest.getSession(true)).thenReturn(httpSession);

        UserDto dto = authService.login("operator", "correctPassword", httpRequest);

        assertThat(dto.username()).isEqualTo("operator");
        assertThat(dto.role()).isEqualTo("OPERATOR");
        verify(auditService).record(eq("operator"), eq("USER_LOGIN"), eq("USER"), eq("1"), any());
    }

    @Test
    @DisplayName("Login with wrong password throws BadCredentialsException")
    void shouldFailLoginWithWrongPassword() {
        String hashed = passwordEncoder.encode("correctPassword");
        UserRecord user = new UserRecord(
            1L, "operator", "op@example.com", hashed, "OPERATOR", clock.instant(), clock.instant()
        );

        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("operator", "wrongPassword", httpRequest))
            .isInstanceOf(BadCredentialsException.class);
    }
}

