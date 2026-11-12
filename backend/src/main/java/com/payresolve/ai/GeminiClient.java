package com.payresolve.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final String API_KEY_HEADER = "x-goog-api-key";

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GeminiClient(
        @Value("${payresolve.ai.gemini.api-key:${GEMINI_API_KEY:${spring.ai.google.genai.api-key:}}}") String apiKey,
        @Value("${payresolve.ai.gemini.model:${GEMINI_MODEL:${AI_MODEL:gemini-3.5-flash}}}") String model,
        @Value("${payresolve.ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta/models}") String baseUrl,
        ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model != null && !model.isBlank() ? model.trim() : "gemini-3.5-flash";
        this.objectMapper = objectMapper;
        String effectiveBase = (baseUrl != null && !baseUrl.isBlank())
            ? baseUrl.trim()
            : "https://generativelanguage.googleapis.com/v1beta/models";
        this.restClient = LlmHttpSupport.buildRestClient(
            effectiveBase,
            LlmHttpSupport.DEFAULT_CONNECT_TIMEOUT_MS,
            LlmHttpSupport.DEFAULT_READ_TIMEOUT_MS
        );
    }

    public GeminiClient(String apiKey, String model, ObjectMapper objectMapper) {
        this(apiKey, model, "https://generativelanguage.googleapis.com/v1beta/models", objectMapper);
    }

    public boolean isAvailable() {
        return !resolveApiKey().isBlank();
    }

    public String resolveApiKey() {
        return LlmHttpSupport.resolveApiKey(apiKey, "GEMINI_API_KEY");
    }

    public String getModel() {
        return model;
    }

    public LlmResponse chatCompletion(String systemPrompt, String userPrompt, boolean jsonMode) {
        String combinedPrompt = (systemPrompt != null && !systemPrompt.isBlank())
            ? systemPrompt + "\n\n" + userPrompt
            : userPrompt;

        Map<String, Object> payload = buildGenerateContentPayload(
            List.of(Map.of("text", combinedPrompt)),
            jsonMode
        );
        return executeGenerateContent("chat completion", payload);
    }

    public LlmResponse analyzeMultimodal(String prompt, String mimeType, String base64Data, boolean jsonMode) {
        Map<String, Object> inlineData = Map.of(
            "mimeType", mimeType,
            "data", base64Data
        );
        Map<String, Object> payload = buildGenerateContentPayload(
            List.of(Map.of("text", prompt), Map.of("inlineData", inlineData)),
            jsonMode
        );
        return executeGenerateContent("multimodal analysis", payload);
    }

    private Map<String, Object> buildGenerateContentPayload(List<Map<String, Object>> parts, boolean jsonMode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contents", List.of(Map.of("parts", parts)));

        Map<String, Object> genConfig = new LinkedHashMap<>();
        genConfig.put("temperature", 0.1);
        if (jsonMode) {
            genConfig.put("responseMimeType", "application/json");
        }
        payload.put("generationConfig", genConfig);
        return payload;
    }

    private LlmResponse executeGenerateContent(String operation, Map<String, Object> payload) {
        String activeKey = resolveApiKey();
        if (activeKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured for " + operation);
        }

        long start = System.currentTimeMillis();
        try {
            return LlmHttpSupport.executeWithTransientRetry(() -> {
                String responseStr = restClient.post()
                    .uri("/" + model + ":generateContent")
                    .header(API_KEY_HEADER, activeKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

                long latency = System.currentTimeMillis() - start;
                JsonNode root = objectMapper.readTree(responseStr);
                String content = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");

                Integer promptTokens = root.path("usageMetadata").path("promptTokenCount").isNumber()
                    ? root.path("usageMetadata").path("promptTokenCount").asInt() : null;
                Integer completionTokens = root.path("usageMetadata").path("candidatesTokenCount").isNumber()
                    ? root.path("usageMetadata").path("candidatesTokenCount").asInt() : null;

                return new LlmResponse(content, "gemini", model, promptTokens, completionTokens, latency, false, null);
            });
        } catch (RuntimeException e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("Gemini {} failed after {} ms: {}", operation, latency, LlmHttpSupport.classifyError(e));
            throw e;
        }
    }
}
