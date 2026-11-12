package com.payresolve.ai;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LlmRouterWireMockTest {

    private WireMockServer wireMock;

    @Mock
    private LlmCallRepository llmCallRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T08:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    @DisplayName("Primary Groq call retries once on transient 503 and records primary tokens and latency")
    void groqPrimaryRetriesOnceOnTransient503AndSucceeds() {
        wireMock.stubFor(post(urlEqualTo("/openai/chat/completions"))
            .inScenario("TransientRetry")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(503).withBody("{\"error\":\"overloaded\"}"))
            .willSetStateTo("SECOND_ATTEMPT"));

        wireMock.stubFor(post(urlEqualTo("/openai/chat/completions"))
            .inScenario("TransientRetry")
            .whenScenarioStateIs("SECOND_ATTEMPT")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "model": "openai/gpt-oss-120b",
                      "choices": [{"message": {"content": "{\\"status\\":\\"CAUSE_FOUND\\"}"}}],
                      "usage": {"prompt_tokens": 310, "completion_tokens": 120}
                    }
                    """)));

        GroqClient groqClient = new GroqClient(
            "gsk_test_wiremock_valid_key_12345",
            "openai/gpt-oss-120b",
            wireMock.baseUrl() + "/openai",
            objectMapper
        );
        GeminiClient geminiClient = new GeminiClient(
            "AIza_test_wiremock_valid_key_12345",
            "gemini-3.5-flash",
            wireMock.baseUrl() + "/gemini",
            objectMapper
        );
        LlmRouter router = new LlmRouter(groqClient, geminiClient, llmCallRepository, clock, "groq");

        LlmResponse resp = router.routeTextInvestigation("PR-0142", "sys", "usr", "hash-1", false);

        assertThat(resp.provider()).isEqualTo("groq");
        assertThat(resp.model()).isEqualTo("openai/gpt-oss-120b");
        assertThat(resp.fallbackUsed()).isFalse();
        assertThat(resp.promptTokens()).isEqualTo(310);
        assertThat(resp.completionTokens()).isEqualTo(120);
        wireMock.verify(2, postRequestedFor(urlEqualTo("/openai/chat/completions")));
        wireMock.verify(0, postRequestedFor(urlPathMatching("/gemini/.*")));
    }

    @Test
    @DisplayName("Groq 401 triggers Gemini fallback without retrying 401 and records safe error class")
    void groq401FallsBackToGeminiWithSafeReason() {
        wireMock.stubFor(post(urlEqualTo("/openai/chat/completions"))
            .willReturn(aResponse().withStatus(401).withBody("{\"error\":{\"message\":\"Invalid API Key\"}}")));

        wireMock.stubFor(post(urlEqualTo("/gemini/gemini-3.5-flash:generateContent"))
            .withHeader("x-goog-api-key", equalTo("AIza_secret_must_never_leak"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "candidates": [{"content": {"parts": [{"text": "{\\"status\\":\\"CAUSE_FOUND\\"}"}]}}],
                      "usageMetadata": {"promptTokenCount": 420, "candidatesTokenCount": 180}
                    }
                    """)));

        GroqClient groqClient = new GroqClient(
            "gsk_secret_key_must_not_leak_123",
            "openai/gpt-oss-120b",
            wireMock.baseUrl() + "/openai",
            objectMapper
        );
        GeminiClient geminiClient = new GeminiClient(
            "AIza_secret_must_never_leak",
            "gemini-3.5-flash",
            wireMock.baseUrl() + "/gemini",
            objectMapper
        );
        LlmRouter router = new LlmRouter(groqClient, geminiClient, llmCallRepository, clock, "groq");

        LlmResponse resp = router.routeTextInvestigation("PR-0142", "sys", "usr", "hash-2", false);

        assertThat(resp.provider()).isEqualTo("gemini");
        assertThat(resp.fallbackUsed()).isTrue();
        assertThat(resp.fallbackReason()).contains("HTTP 401 Unauthorized");
        assertThat(resp.fallbackReason()).doesNotContain("gsk_secret_key_must_not_leak_123");
        assertThat(resp.fallbackReason()).doesNotContain("AIza_secret_must_never_leak");
        assertThat(resp.promptTokens()).isEqualTo(420);
        assertThat(resp.completionTokens()).isEqualTo(180);
        wireMock.verify(1, postRequestedFor(urlEqualTo("/openai/chat/completions")));
        wireMock.verify(1, postRequestedFor(urlEqualTo("/gemini/gemini-3.5-flash:generateContent")));
    }

    @Test
    @DisplayName("When both Groq and Gemini fail, rule_fallback is recorded cleanly without fake tokens or model")
    void bothProvidersFailingFallsBackToRuleBasedWithNullTokens() {
        wireMock.stubFor(post(urlEqualTo("/openai/chat/completions"))
            .willReturn(aResponse().withStatus(401)));
        wireMock.stubFor(post(urlEqualTo("/gemini/gemini-3.5-flash:generateContent"))
            .willReturn(aResponse().withStatus(404)));

        GroqClient groqClient = new GroqClient(
            "gsk_secret_key_must_not_leak_123",
            "openai/gpt-oss-120b",
            wireMock.baseUrl() + "/openai",
            objectMapper
        );
        GeminiClient geminiClient = new GeminiClient(
            "AIza_secret_must_never_leak",
            "gemini-3.5-flash",
            wireMock.baseUrl() + "/gemini",
            objectMapper
        );
        LlmRouter router = new LlmRouter(groqClient, geminiClient, llmCallRepository, clock, "groq");

        LlmResponse resp = router.routeTextInvestigation("PR-0142", "sys", "usr", "hash-3", false);

        assertThat(resp.provider()).isEqualTo("rule_fallback");
        assertThat(resp.model()).isEqualTo("deterministic-rules");
        assertThat(resp.fallbackUsed()).isTrue();
        assertThat(resp.promptTokens()).isNull();
        assertThat(resp.completionTokens()).isNull();
        assertThat(resp.fallbackReason()).contains("Groq: HTTP 401 Unauthorized");
        assertThat(resp.fallbackReason()).contains("Gemini: HTTP 404 Model Not Found");
        assertThat(resp.fallbackReason()).doesNotContain("gsk_secret_key_must_not_leak_123");
        assertThat(resp.fallbackReason()).doesNotContain("AIza_secret_must_never_leak");

        ArgumentCaptor<LlmCallRecord> captor = ArgumentCaptor.forClass(LlmCallRecord.class);
        verify(llmCallRepository, atLeastOnce()).save(captor.capture());
        List<LlmCallRecord> saved = captor.getAllValues();
        assertThat(saved.get(saved.size() - 1).provider()).isEqualTo("rule_fallback");
        assertThat(saved.get(saved.size() - 1).status()).isEqualTo("DEGRADED");
    }
}

