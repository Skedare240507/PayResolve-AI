package com.payresolve.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerTest.TestEndpointController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    public static class TestEndpointController {

        @GetMapping("/test-sample/{id}")
        public String sample(@PathVariable String id) {
            if ("not-found".equals(id)) {
                throw new ResourceNotFoundException("Entity " + id + " does not exist");
            }
            if ("invalid".equals(id)) {
                throw new ValidationException("Field id has invalid characters");
            }
            if ("illegal-state".equals(id)) {
                throw new IllegalStateException("Order is already captured");
            }
            return "ok: " + id;
        }
    }

    @Test
    @DisplayName("Should map RestController under /api prefix and return 200 on valid request")
    void shouldMapUnderApiPrefix() throws Exception {
        mockMvc.perform(get("/api/test-sample/valid-123"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should translate ResourceNotFoundException to RFC 9457 404 ProblemDetail")
    void shouldReturn404ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/test-sample/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.type").value("urn:payresolve:not-found"))
                .andExpect(jsonPath("$.detail").value("Entity not-found does not exist"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should translate ValidationException to RFC 9457 400 ProblemDetail")
    void shouldReturn400ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/test-sample/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation Failure"))
                .andExpect(jsonPath("$.type").value("urn:payresolve:bad-request"))
                .andExpect(jsonPath("$.detail").value("Field id has invalid characters"));
    }

    @Test
    @DisplayName("Should translate IllegalStateException to RFC 9457 409 ProblemDetail")
    void shouldReturn409ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/test-sample/illegal-state"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.type").value("urn:payresolve:conflict"));
    }
}
