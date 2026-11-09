package com.payresolve.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Component
public class GroqClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GroqClient(
        @Value("${payresolve.ai.groq.api-key:${GROQ_API_KEY:}}") String apiKey,
        @Value("${payresolve.ai.groq.model:${GROQ_MODEL:openai/gpt-oss-120b}}") String model,
        @Value("${payresolve.ai.groq.base-url:https://api.groq.com/openai/v1}") String baseUrl,
        ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .build();
    }

    public boolean isAvailable() {
        return !resolveApiKey().isBlank();
    }

    public String resolveApiKey() {
        if (isValidKey(apiKey)) {
            return apiKey;
        }
        String sysEnv = System.getenv("GROQ_API_KEY");
        if (isValidKey(sysEnv)) {
            return sysEnv.trim();
        }
        return readFromEnvFile("GROQ_API_KEY");
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
            throw new IllegalStateException("Groq API key is not configured");
        }

        long start = System.currentTimeMillis();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("temperature", 0.1);

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));
        payload.put("messages", messages);

        if (jsonMode) {
            payload.put("response_format", Map.of("type", "json_object"));
        }

        try {
            String responseStr = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + activeKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);

            long latency = System.currentTimeMillis() - start;

            JsonNode root = objectMapper.readTree(responseStr);
            String content = root.path("choices").get(0).path("message").path("content").asText();

            Integer promptTokens = root.path("usage").path("prompt_tokens").isNumber()
                ? root.path("usage").path("prompt_tokens").asInt() : null;
            Integer completionTokens = root.path("usage").path("completion_tokens").isNumber()
                ? root.path("usage").path("completion_tokens").asInt() : null;

            return new LlmResponse(content, "groq", model, promptTokens, completionTokens, latency, false, null);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("Groq chat completion call failed after {} ms: {}", latency, e.getMessage());
            throw new RuntimeException("Groq API error: " + e.getMessage(), e);
        }
    }
}

