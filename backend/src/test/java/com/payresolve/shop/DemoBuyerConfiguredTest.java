package com.payresolve.shop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "payresolve.demo-mode=true",
    "payresolve.demo.buyer-email=synthetic-buyer@example.com",
    "payresolve.demo.buyer-password=synthetic-pass-only"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoBuyerConfiguredTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/shop/demo-buyer returns configured sandbox credentials for signed-in users and 401 anonymously")
    void returnsCredentialsOnlyWhenSignedIn() throws Exception {
        mockMvc.perform(get("/api/shop/demo-buyer"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/shop/demo-buyer").with(user("viewer").roles("VIEWER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("synthetic-buyer@example.com"))
            .andExpect(jsonPath("$.password").value("synthetic-pass-only"))
            .andExpect(jsonPath("$.note").value("PayPal Sandbox only. Not a real account. Use these to approve the test payment."));
    }
}

