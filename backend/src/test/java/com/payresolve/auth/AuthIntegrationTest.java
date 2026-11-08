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
        mockMvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/auth/login with wrong password returns 401 Unauthorized")
    void shouldFailLoginWithBadCredentials() throws Exception {
        LoginRequest request = new LoginRequest("operator", "wrong_password");

        mockMvc.perform(post("/api/auth/login")
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
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(uniqueUser))
            .andExpect(jsonPath("$.role").value("VIEWER")) // Strictly VIEWER role
            .andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    @DisplayName("Forgot password and reset password flow with on-screen demo link")
    void shouldCompleteForgotPasswordFlow() throws Exception {
        String uniqueUser = "resetuser_" + System.currentTimeMillis();
        SignUpRequest signUp = new SignUpRequest(uniqueUser, uniqueUser + "@test.com", "oldPassword123");
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signUp)))
            .andExpect(status().isOk());

        // 1. Request password reset
        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest(uniqueUser);
        MvcResult forgotResult = mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(forgotReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resetToken").isString())
            .andExpect(jsonPath("$.resetUrl").isString())
            .andExpect(jsonPath("$.demoNotice").isString())
            .andReturn();

        JsonNode forgotJson = objectMapper.readTree(forgotResult.getResponse().getContentAsString());
        String token = forgotJson.get("resetToken").asText();
        assertThat(token).isNotBlank();

        // 2. Reset password
        ResetPasswordRequest resetReq = new ResetPasswordRequest(token, "brandNewPassword123");
        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resetReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        // 3. Verify login works with the new password
        LoginRequest loginNew = new LoginRequest(uniqueUser, "brandNewPassword123");
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginNew)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(uniqueUser));
    }
}

