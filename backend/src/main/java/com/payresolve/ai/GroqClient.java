package com.payresolve.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GroqClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GroqClient(
        @Value("${payresolve.ai.groq.api-key:${GROQ_API_KEY:}}") String apiKey,
        @Value("${payresolve.ai.groq.model:${GROQ_MODEL:openai/gpt-oss-120b}}") String model,
        @Value("${payresolve.ai.groq.base-url:https://api.groq.com/openai/v1}") String baseUrl,
        ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model != null && !model.isBlank() ? model.trim() : "openai/gpt-oss-120b";
        this.objectMapper = objectMapper;
        this.restClient = LlmHttpSupport.buildRestClient(
            baseUrl,
            LlmHttpSupport.DEFAULT_CONNECT_TIMEOUT_MS,
            LlmHttpSupport.DEFAULT_READ_TIMEOUT_MS
        );
    }

    public boolean isAvailable() {
        return !resolveApiKey().isBlank();
    }

    public String resolveApiKey() {
        return LlmHttpSupport.resolveApiKey(apiKey, "GROQ_API_KEY");
    }

    public String getModel() {
        return model;
    }

    public LlmResponse chatCompletion(String systemPrompt, String userPrompt, boolean jsonMode) {
        String activeKey = resolveApiKey();
        if (activeKey.isBlank()) {
            throw new IllegalStateException("Groq API key is not configured");
        }

        long start = System.currentTimeMillis();
        try {
            return invokeWithFallbackFormat(activeKey, systemPrompt, userPrompt, jsonMode, start);
        } catch (RuntimeException e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("Groq chat completion call failed after {} ms: {}", latency, LlmHttpSupport.classifyError(e));
            throw e;
        }
    }

    private LlmResponse invokeWithFallbackFormat(
        String activeKey,
        String systemPrompt,
        String userPrompt,
        boolean jsonMode,
        long start
    ) {
        try {
            return LlmHttpSupport.executeWithTransientRetry(
                () -> sendChatRequest(activeKey, systemPrompt, userPrompt, jsonMode, start)
            );
        } catch (RestClientResponseException ex) {
            if (jsonMode && ex.getStatusCode().value() == 400
                && ex.getResponseBodyAsString().contains("json_validate_failed")) {
                log.warn("Groq model {} rejected strict json_object format; retrying once without response_format", model);
                return LlmHttpSupport.executeWithTransientRetry(
                    () -> sendChatRequest(activeKey, systemPrompt, userPrompt, false, start)
                );
            }
            throw ex;
        }
    }

    private LlmResponse sendChatRequest(
        String activeKey,
        String systemPrompt,
        String userPrompt,
        boolean includeJsonResponseFormat,
        long start
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("temperature", 0.1);

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));
        payload.put("messages", messages);

        if (includeJsonResponseFormat) {
            payload.put("response_format", Map.of("type", "json_object"));
        }

        String responseStr = restClient.post()
            .uri("/chat/completions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + activeKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .body(String.class);

        long latency = System.currentTimeMillis() - start;
        JsonNode root = objectMapper.readTree(responseStr);
        String content = root.path("choices").path(0).path("message").path("content").asText("");

        Integer promptTokens = root.path("usage").path("prompt_tokens").isNumber()
            ? root.path("usage").path("prompt_tokens").asInt() : null;
        Integer completionTokens = root.path("usage").path("completion_tokens").isNumber()
            ? root.path("usage").path("completion_tokens").asInt() : null;

        return new LlmResponse(content, "groq", model, promptTokens, completionTokens, latency, false, null);
    }
}
