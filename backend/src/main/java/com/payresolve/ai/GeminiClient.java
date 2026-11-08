package com.payresolve.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiClient(
        @Value("${payresolve.ai.gemini.api-key:${GEMINI_API_KEY:${spring.ai.google.genai.api-key:}}}") String apiKey,
        @Value("${payresolve.ai.gemini.model:${AI_MODEL:gemini-2.0-flash}}") String model,
        ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
            .baseUrl("https://generativelanguage.googleapis.com/v1beta/models")
            .build();
    }

    public boolean isAvailable() {
        return !apiKey.isBlank();
    }

    public String getModel() {
        return model;
    }

    public LlmResponse chatCompletion(String systemPrompt, String userPrompt, boolean jsonMode) {
        if (!isAvailable()) {
            throw new IllegalStateException("Gemini API key is not configured");
        }

        long start = System.currentTimeMillis();

        String combinedPrompt = (systemPrompt != null && !systemPrompt.isBlank())
            ? systemPrompt + "\n\n" + userPrompt
            : userPrompt;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contents", List.of(
            Map.of("parts", List.of(Map.of("text", combinedPrompt)))
        ));

        Map<String, Object> genConfig = new LinkedHashMap<>();
        genConfig.put("temperature", 0.1);
        if (jsonMode) {
            genConfig.put("responseMimeType", "application/json");
        }
        payload.put("generationConfig", genConfig);

        try {
            String responseStr = restClient.post()
                .uri("/" + model + ":generateContent?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);

            long latency = System.currentTimeMillis() - start;

            JsonNode root = objectMapper.readTree(responseStr);
            String content = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();

            Integer promptTokens = root.path("usageMetadata").path("promptTokenCount").isNumber()
                ? root.path("usageMetadata").path("promptTokenCount").asInt() : null;
            Integer completionTokens = root.path("usageMetadata").path("candidatesTokenCount").isNumber()
                ? root.path("usageMetadata").path("candidatesTokenCount").asInt() : null;

            return new LlmResponse(content, "gemini", model, promptTokens, completionTokens, latency, false, null);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("Gemini chat completion call failed after {} ms: {}", latency, e.getMessage());
            throw new RuntimeException("Gemini API error: " + e.getMessage(), e);
        }
    }

    public LlmResponse analyzeMultimodal(String prompt, String mimeType, String base64Data, boolean jsonMode) {
        if (!isAvailable()) {
            throw new IllegalStateException("Gemini API key is not configured for multimodal analysis");
        }

        long start = System.currentTimeMillis();

        Map<String, Object> inlineData = Map.of(
            "mimeType", mimeType,
            "data", base64Data
        );

        List<Map<String, Object>> parts = List.of(
            Map.of("text", prompt),
            Map.of("inlineData", inlineData)
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contents", List.of(Map.of("parts", parts)));

        Map<String, Object> genConfig = new LinkedHashMap<>();
        genConfig.put("temperature", 0.1);
        if (jsonMode) {
            genConfig.put("responseMimeType", "application/json");
        }
        payload.put("generationConfig", genConfig);

        try {
            String responseStr = restClient.post()
                .uri("/" + model + ":generateContent?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);

            long latency = System.currentTimeMillis() - start;

            JsonNode root = objectMapper.readTree(responseStr);
            String content = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();

            Integer promptTokens = root.path("usageMetadata").path("promptTokenCount").isNumber()
                ? root.path("usageMetadata").path("promptTokenCount").asInt() : null;
            Integer completionTokens = root.path("usageMetadata").path("candidatesTokenCount").isNumber()
                ? root.path("usageMetadata").path("candidatesTokenCount").asInt() : null;

            return new LlmResponse(content, "gemini", model, promptTokens, completionTokens, latency, false, null);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("Gemini multimodal call failed after {} ms: {}", latency, e.getMessage());
            throw new RuntimeException("Gemini multimodal error: " + e.getMessage(), e);
        }
    }
}

