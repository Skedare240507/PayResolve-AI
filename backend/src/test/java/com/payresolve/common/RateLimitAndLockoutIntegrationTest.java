package com.payresolve.common;

import com.payresolve.auth.LoginAttemptService;
import com.payresolve.cases.CaseService;
import com.payresolve.paypal.PayPalWebhookVerificationClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitAndLockoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private CaseService caseService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PayPalWebhookVerificationClient verificationClient;

    @BeforeEach
    void setUp() {
        rateLimitService.resetAll();
        loginAttemptService.resetAll();
        caseService.seedDemoCases();
        jdbcTemplate.update("DELETE FROM case_attachments");
    }

    @Test
    @DisplayName("Login rate limit enforces 10 requests per 10 minutes per IP with 429 and Retry-After")
    void loginIpRateLimit() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .with(csrf())
                    .with(req -> { req.setRemoteAddr("198.51.100.10"); return req; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"operator\",\"password\":\"wrong" + i + "\"}"))
                .andExpect(status().isUnauthorized());
        }

        var blockedResult = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .with(req -> { req.setRemoteAddr("198.51.100.10"); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"operator\",\"password\":\"wrong11\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andReturn();
        org.assertj.core.api.Assertions.assertThat(
            Long.parseLong(blockedResult.getResponse().getHeader("Retry-After"))
        ).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("Five consecutive login failures lock username for 15 minutes with uniform 401 message")
    void loginFiveFailureLockoutUniformError() throws Exception {
        for (int i = 0; i < 5; i++) {
            final String ip = "198.51.100." + (20 + i);
            mockMvc.perform(post("/api/auth/login")
                    .with(csrf())
                    .with(req -> { req.setRemoteAddr(ip); return req; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"operator\",\"password\":\"bad_pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
        }

        // Even with the CORRECT password from another IP, locked account returns uniform 401 message
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .with(req -> { req.setRemoteAddr("198.51.100.99"); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"operator\",\"password\":\"test_operator_password\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    @DisplayName("Signup and forgot-password enforce 5 requests per hour per IP returning 429 + Retry-After")
    void signupAndForgotPasswordIpRateLimits() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/signup")
                    .with(csrf())
                    .with(req -> { req.setRemoteAddr("203.0.113.50"); return req; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"rluser_" + System.nanoTime() + "_" + i + "\",\"email\":\"rl_" + System.nanoTime() + "_" + i + "@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/auth/signup")
                .with(csrf())
                .with(req -> { req.setRemoteAddr("203.0.113.50"); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"rluser_blocked\",\"email\":\"rl_blocked@example.com\",\"password\":\"password123\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/forgot-password")
                    .with(csrf())
                    .with(req -> { req.setRemoteAddr("203.0.113.51"); return req; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"identifier\":\"viewer\"}"));
        }
        mockMvc.perform(post("/api/auth/forgot-password")
                .with(csrf())
                .with(req -> { req.setRemoteAddr("203.0.113.51"); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"viewer\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("Per-user rate limits enforce /api/agent/chat (20/h), /investigate (10/h), and attachments (10/h)")
    void perUserRateLimitsEnforced() throws Exception {
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/agent/chat")
                    .with(user("rl-chat-user").roles("VIEWER"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"message\":\"status?\"}"))
                .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/agent/chat")
                .with(user("rl-chat-user").roles("VIEWER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"status?\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/cases/PR-0142/investigate")
                    .with(user("rl-inv-user").roles("VIEWER"))
                    .with(csrf()))
                .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/cases/PR-0142/investigate")
                .with(user("rl-inv-user").roles("VIEWER"))
                .with(csrf()))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));

        byte[] tinyPng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        java.util.List<String> targetCases = java.util.List.of("PR-0142", "PR-0141", "PR-0140", "PR-0138", "PR-0137");
        for (int i = 0; i < 10; i++) {
            String targetCase = targetCases.get(i % targetCases.size());
            MockMultipartFile file = new MockMultipartFile("file", "proof" + i + ".png", "image/png", tinyPng);
            mockMvc.perform(multipart("/api/cases/" + targetCase + "/attachments")
                    .file(file)
                    .with(user("rl-upload-op").roles("OPERATOR"))
                    .with(csrf()))
                .andExpect(status().isCreated());
        }
        MockMultipartFile eleventhFile = new MockMultipartFile("file", "proof11.png", "image/png", tinyPng);
        mockMvc.perform(multipart("/api/cases/PR-0142/attachments")
                .file(eleventhFile)
                .with(user("rl-upload-op").roles("OPERATOR"))
                .with(csrf()))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("POST /api/webhooks/paypal returns 413 when request body exceeds 256 KB")
    void webhookRejectsPayloadsOver256KbWith413() throws Exception {
        when(verificationClient.verifySignature(any(), any())).thenReturn(false);

        byte[] oversized = new byte[256 * 1024 + 10];
        java.util.Arrays.fill(oversized, (byte) 'a');
        String body = "{\"id\":\"WH-BIG\",\"note\":\"" + new String(oversized, StandardCharsets.US_ASCII) + "\"}";

        mockMvc.perform(post("/api/webhooks/paypal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isContentTooLarge());
    }
}
