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

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            userRepository,
            resetTokenRepository,
            auditService,
            authProperties,
            passwordEncoder,
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
    @DisplayName("Forgot password generates reset token with expiry and demo mode notice")
    void shouldGenerateResetTokenWithDemoNotice() {
        UserRecord user = new UserRecord(
            1L, "operator", "operator@example.com", "hash", "OPERATOR", clock.instant(), clock.instant()
        );
        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(user));

        ForgotPasswordResponse response = authService.requestPasswordReset("operator");

        assertThat(response.resetToken()).isNotBlank();
        assertThat(response.resetUrl()).contains("/reset-password?token=");
        assertThat(response.demoNotice()).contains("Demo mode notice");

        verify(resetTokenRepository).insert(any());
    }

    @Test
    @DisplayName("Reset password updates password hash and marks token used")
    void shouldResetPassword() {
        PasswordResetTokenRecord tokenRecord = new PasswordResetTokenRecord(
            10L, 1L, "valid-token-123", clock.instant().plus(15, ChronoUnit.MINUTES), null, clock.instant()
        );

        when(resetTokenRepository.findByToken("valid-token-123")).thenReturn(Optional.of(tokenRecord));

        authService.resetPassword("valid-token-123", "newPassword456");

        verify(userRepository).updatePassword(eq(1L), anyString());
        verify(resetTokenRepository).markUsed(eq(10L), eq(clock.instant()));
        verify(auditService).record(eq("system"), eq("PASSWORD_RESET"), eq("USER"), eq("1"), any());
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

