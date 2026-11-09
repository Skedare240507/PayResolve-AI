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
        @Value("${payresolve.ai.gemini.model:${GEMINI_MODEL:${AI_MODEL:gemini-3.5-flash}}}") String model,
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
        return !resolveApiKey().isBlank();
    }

    public String resolveApiKey() {
        if (isValidKey(apiKey)) {
            return apiKey;
        }
        String sysEnv = System.getenv("GEMINI_API_KEY");
        if (isValidKey(sysEnv)) {
            return sysEnv.trim();
        }
        return readFromEnvFile("GEMINI_API_KEY");
    }

    private static boolean isValidKey(String key) {
        if (key == null || key.isBlank()) return false;
        String lower = key.toLowerCase();
        return !lower.startsWith("mock_")
            && !lower.contains("placeholder")
            && !lower.contains("your_")
            && !lower.contains("youractual")
            && !lower.contains("your-actual")
            && key.length() > 15;
    }

    private static String readFromEnvFile(String varName) {
        for (String candidate : List.of(".env", "../.env")) {
            java.nio.file.Path p = java.nio.file.Path.of(candidate);
            if (java.nio.file.Files.exists(p)) {
                try {
                    for (String line : java.nio.file.Files.readAllLines(p)) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith(varName + "=")) {
                            String val = trimmed.substring(varName.length() + 1).trim();
                            if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                                val = val.substring(1, val.length() - 1);
                            }
                            if (isValidKey(val)) {
                                return val;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return "";
    }

    public String getModel() {
        return model;
    }

    public LlmResponse chatCompletion(String systemPrompt, String userPrompt, boolean jsonMode) {
        String activeKey = resolveApiKey();
        if (activeKey.isBlank()) {
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
                .uri("/" + model + ":generateContent?key=" + activeKey)
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
        String activeKey = resolveApiKey();
        if (activeKey.isBlank()) {
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
                .uri("/" + model + ":generateContent?key=" + activeKey)
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

