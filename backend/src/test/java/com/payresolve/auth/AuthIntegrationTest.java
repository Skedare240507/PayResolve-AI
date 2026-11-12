package com.payresolve.auth;

import com.payresolve.auth.dto.ForgotPasswordRequest;
import com.payresolve.auth.dto.LoginRequest;
import com.payresolve.auth.dto.ResetPasswordRequest;
import com.payresolve.auth.dto.SignUpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/auth/login with seeded credentials establishes session and returns UserDto")
    void shouldLoginWithSeededCredentials() throws Exception {
        LoginRequest request = new LoginRequest("operator", "test_operator_password");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("operator"))
            .andExpect(jsonPath("$.role").value("OPERATOR"))
            .andExpect(jsonPath("$.authenticated").value(true))
            .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession();
        assertThat(session).isNotNull();

        // Verify /api/auth/me using the active session
        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("operator"))
            .andExpect(jsonPath("$.role").value("OPERATOR"));

        // Logout
        mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/auth/login with wrong password returns 401 Unauthorized")
    void shouldFailLoginWithBadCredentials() throws Exception {
        LoginRequest request = new LoginRequest("operator", "wrong_password");

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.title").value("Unauthorized"))
            .andExpect(jsonPath("$.type").value("urn:payresolve:unauthorized"));
    }

    @Test
    @DisplayName("POST /api/auth/signup creates a VIEWER account only")
    void shouldSignUpViewerAccount() throws Exception {
        String uniqueUser = "viewer_" + System.currentTimeMillis();
        SignUpRequest request = new SignUpRequest(uniqueUser, uniqueUser + "@test.com", "securePass123");

        mockMvc.perform(post("/api/auth/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(uniqueUser))
            .andExpect(jsonPath("$.role").value("VIEWER")) // Strictly VIEWER role
            .andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    @DisplayName("Default forgot-password never exposes token or account existence and returns generic message")
    void defaultForgotPasswordHidesTokenAndAccountExistence() throws Exception {
        ForgotPasswordRequest existingViewer = new ForgotPasswordRequest("viewer");
        ForgotPasswordRequest existingOperator = new ForgotPasswordRequest("operator");
        ForgotPasswordRequest missingUser = new ForgotPasswordRequest("nonexistent_user_xyz");

        for (ForgotPasswordRequest req : new ForgotPasswordRequest[]{existingViewer, existingOperator, missingUser}) {
            mockMvc.perform(post("/api/auth/forgot-password")
                    .with(csrf())
                    .with(r -> { r.setRemoteAddr("198.51.100.77"); return r; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(AuthService.GENERIC_RESET_MESSAGE))
                .andExpect(jsonPath("$.resetToken").doesNotExist())
                .andExpect(jsonPath("$.resetUrl").doesNotExist())
                .andExpect(jsonPath("$.demoNotice").doesNotExist());
        }
    }

    @Test
    @DisplayName("Password reset invalidates older tokens, signs out active sessions, and stores only SHA-256 hash")
    void shouldInvalidatePriorTokensAndActiveSessionsOnReset(
        @Autowired AuthProperties authProperties,
        @Autowired PasswordResetTokenRepository tokenRepository
    ) throws Exception {
        String uniqueUser = "resetuser_" + System.currentTimeMillis();
        SignUpRequest signUp = new SignUpRequest(uniqueUser, uniqueUser + "@test.com", "oldPassword123");
        mockMvc.perform(post("/api/auth/signup")
                .with(csrf())
                .with(r -> { r.setRemoteAddr("198.51.100.81"); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signUp)))
            .andExpect(status().isOk());

        // Establish two active sessions for this user
        MvcResult login1 = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .with(r -> { r.setRemoteAddr("198.51.100.82"); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(uniqueUser, "oldPassword123"))))
            .andExpect(status().isOk())
            .andReturn();
        MockHttpSession session1 = (MockHttpSession) login1.getRequest().getSession(false);

        MvcResult login2 = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .with(r -> { r.setRemoteAddr("198.51.100.83"); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(uniqueUser, "oldPassword123"))))
            .andExpect(status().isOk())
            .andReturn();
        MockHttpSession session2 = (MockHttpSession) login2.getRequest().getSession(false);

        boolean prevDemoLinks = authProperties.isDemoResetLinks();
        authProperties.setDemoResetLinks(true);
        try {
            // Issue first token, then second token -> first token must become invalidated
            MvcResult firstForgot = mockMvc.perform(post("/api/auth/forgot-password")
                    .with(csrf())
                    .with(r -> { r.setRemoteAddr("198.51.100.84"); return r; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(uniqueUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken").isString())
                .andReturn();
            String firstToken = objectMapper.readTree(firstForgot.getResponse().getContentAsString()).get("resetToken").asText();

            MvcResult secondForgot = mockMvc.perform(post("/api/auth/forgot-password")
                    .with(csrf())
                    .with(r -> { r.setRemoteAddr("198.51.100.85"); return r; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(uniqueUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken").isString())
                .andExpect(jsonPath("$.resetUrl").isString())
                .andExpect(jsonPath("$.demoNotice").isString())
                .andReturn();
            String secondToken = objectMapper.readTree(secondForgot.getResponse().getContentAsString()).get("resetToken").asText();

            // Raw token is NOT stored in token_hash; SHA-256 hash IS stored
            assertThat(tokenRepository.findByToken(secondToken)).isEmpty();
            assertThat(tokenRepository.findByToken(AuthService.sha256Hex(secondToken))).isPresent();

            // Older token was invalidated when second token was issued
            mockMvc.perform(post("/api/auth/reset-password")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new ResetPasswordRequest(firstToken, "brandNewPassword123"))))
                .andExpect(status().isBadRequest());

            // Valid second token succeeds
            mockMvc.perform(post("/api/auth/reset-password")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new ResetPasswordRequest(secondToken, "brandNewPassword123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

            // Previously active sessions are now invalidated
            mockMvc.perform(get("/api/auth/me").session(session1)).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/auth/me").session(session2)).andExpect(status().isUnauthorized());

            // Login works with new password
            mockMvc.perform(post("/api/auth/login")
                    .with(csrf())
                    .with(r -> { r.setRemoteAddr("198.51.100.86"); return r; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginRequest(uniqueUser, "brandNewPassword123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(uniqueUser));
        } finally {
            authProperties.setDemoResetLinks(prevDemoLinks);
        }
    }
}

