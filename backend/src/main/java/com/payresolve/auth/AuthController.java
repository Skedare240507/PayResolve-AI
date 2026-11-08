package com.payresolve.auth;

import com.payresolve.auth.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller managing authentication, user session checks, viewer sign-up,
 * and demo-mode password reset flows.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Authentication, role-based authorization, and session lifecycle")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate credentials and establish an active HTTP session")
    public ResponseEntity<UserDto> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        UserDto user = authService.login(request.username(), request.password(), httpRequest);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/signup")
    @Operation(summary = "Self-registration: creates an account with VIEWER role only")
    public ResponseEntity<UserDto> signUp(@Valid @RequestBody SignUpRequest request) {
        UserDto user = authService.signUp(request);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Generate a password reset link (displayed on screen in demo mode)")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(
        @Valid @RequestBody ForgotPasswordRequest request
    ) {
        ForgotPasswordResponse response = authService.requestPasswordReset(request.identifier());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset account password using an unexpired, unused reset token")
    public ResponseEntity<Map<String, Object>> resetPassword(
        @Valid @RequestBody ResetPasswordRequest request
    ) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Password has been reset successfully. You can now log in with your new password."
        ));
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate active HTTP session and clear security context")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    @Operation(summary = "Retrieve profile and role of currently authenticated session")
    public ResponseEntity<UserDto> getCurrentUser(Authentication authentication) {
        UserDto user = authService.getCurrentUser(authentication);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/csrf")
    @Operation(summary = "Retrieve active CSRF token for state-mutating requests")
    public ResponseEntity<Map<String, String>> getCsrfToken(CsrfToken csrfToken) {
        if (csrfToken != null) {
            return ResponseEntity.ok(Map.of(
                "token", csrfToken.getToken(),
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName()
            ));
        }
        return ResponseEntity.ok(Map.of("message", "CSRF token not required"));
    }
}

