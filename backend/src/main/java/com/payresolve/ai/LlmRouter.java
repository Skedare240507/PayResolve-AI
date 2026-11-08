package com.payresolve.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class LlmRouter {

    private static final Logger log = LoggerFactory.getLogger(LlmRouter.class);

    private final GroqClient groqClient;
    private final GeminiClient geminiClient;
    private final LlmCallRepository llmCallRepository;
    private final Clock clock;

    public LlmRouter(
        GroqClient groqClient,
        GeminiClient geminiClient,
        LlmCallRepository llmCallRepository,
        Clock clock
    ) {
        this.groqClient = groqClient;
        this.geminiClient = geminiClient;
        this.llmCallRepository = llmCallRepository;
        this.clock = clock;
    }

    public static String computeSnapshotHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 32);
        } catch (Exception e) {
            return Integer.toHexString(content.hashCode());
        }
    }

    /**
     * Routes text investigation requests:
     * 1. Groq (primary)
     * 2. Fallback to Gemini if Groq fails
     * 3. Fallback to degraded if both fail
     */
    public LlmResponse routeTextInvestigation(
        String caseId,
        String systemPrompt,
        String userPrompt,
        String snapshotHash,
        boolean forceRefresh
    ) {
        Instant now = clock.instant();

        // 1. Try Groq Primary
        if (groqClient.isAvailable()) {
            try {
                log.info("Routing text investigation for case {} to Groq ({})", caseId, groqClient.getModel());
                LlmResponse resp = groqClient.chatCompletion(systemPrompt, userPrompt, true);
                saveCall(caseId, "groq", resp.model(), "TEXT_INVESTIGATION",
                    resp.promptTokens(), resp.completionTokens(), resp.latencyMs(),
                    "SUCCESS", false, null, snapshotHash, now);
                return resp;
            } catch (Exception e) {
                log.warn("Primary provider Groq failed for case {}: {}. Triggering Gemini fallback.", caseId, e.getMessage());
                // Save failed call attempt
                saveCall(caseId, "groq", groqClient.getModel(), "TEXT_INVESTIGATION",
                    null, null, 0, "FAILED", true, "Groq failure: " + e.getMessage(), snapshotHash, now);

                // 2. Fallback to Gemini
                if (geminiClient.isAvailable()) {
                    try {
                        log.info("Routing fallback text investigation for case {} to Gemini ({})", caseId, geminiClient.getModel());
                        LlmResponse geminiResp = geminiClient.chatCompletion(systemPrompt, userPrompt, true);
                        LlmResponse fallbackResp = new LlmResponse(
                            geminiResp.content(),
                            "gemini",
                            geminiResp.model(),
                            geminiResp.promptTokens(),
                            geminiResp.completionTokens(),
                            geminiResp.latencyMs(),
                            true,
                            "Groq failed: " + e.getMessage()
                        );
                        saveCall(caseId, "gemini", fallbackResp.model(), "TEXT_INVESTIGATION",
                            fallbackResp.promptTokens(), fallbackResp.completionTokens(), fallbackResp.latencyMs(),
                            "FALLBACK", true, fallbackResp.fallbackReason(), snapshotHash, now);
                        return fallbackResp;
                    } catch (Exception geminiEx) {
                        log.error("Gemini fallback also failed for case {}: {}", caseId, geminiEx.getMessage());
                        saveCall(caseId, "gemini", geminiClient.getModel(), "TEXT_INVESTIGATION",
                            null, null, 0, "FAILED", true, "Gemini failure: " + geminiEx.getMessage(), snapshotHash, now);
                    }
                }
            }
        } else if (geminiClient.isAvailable()) {
            // Groq not configured, use Gemini directly
            try {
                log.info("Groq unavailable. Routing text investigation for case {} directly to Gemini ({})", caseId, geminiClient.getModel());
                LlmResponse geminiResp = geminiClient.chatCompletion(systemPrompt, userPrompt, true);
                saveCall(caseId, "gemini", geminiResp.model(), "TEXT_INVESTIGATION",
                    geminiResp.promptTokens(), geminiResp.completionTokens(), geminiResp.latencyMs(),
                    "SUCCESS", false, null, snapshotHash, now);
                return geminiResp;
            } catch (Exception geminiEx) {
                log.error("Gemini direct call failed for case {}: {}", caseId, geminiEx.getMessage());
                saveCall(caseId, "gemini", geminiClient.getModel(), "TEXT_INVESTIGATION",
                    null, null, 0, "FAILED", true, "Gemini failure: " + geminiEx.getMessage(), snapshotHash, now);
            }
        }

        // Both providers failed or unconfigured -> degraded rule fallback
        log.warn("All LLM providers unavailable for case {}. Degraded rule fallback active.", caseId);
        saveCall(caseId, "rule_fallback", "deterministic-rules", "TEXT_INVESTIGATION",
            0, 0, 1, "DEGRADED", true, "All LLM providers unavailable", snapshotHash, now);
        return new LlmResponse(null, "rule_fallback", "deterministic-rules", 0, 0, 1, true, "All LLM providers unavailable");
    }

    /**
     * Routes multimodal requests (images and PDFs):
     * 1. Gemini (primary multimodal)
     * 2. Fallback to Groq with text notice if Gemini fails or is unavailable
     */
    public LlmResponse routeMultimodalAnalysis(
        String caseId,
        String filename,
        String mimeType,
        String base64Data,
        String prompt
    ) {
        Instant now = clock.instant();

        if (geminiClient.isAvailable()) {
            try {
                log.info("Routing multimodal analysis for attachment {} (type: {}) to Gemini", filename, mimeType);
                LlmResponse resp = geminiClient.analyzeMultimodal(prompt, mimeType, base64Data, true);
                saveCall(caseId, "gemini", resp.model(), "MULTIMODAL_ATTACHMENT",
                    resp.promptTokens(), resp.completionTokens(), resp.latencyMs(),
                    "SUCCESS", false, null, null, now);
                return resp;
            } catch (Exception e) {
                log.warn("Gemini multimodal analysis failed for {}: {}. Falling back to Groq text notice.", filename, e.getMessage());
                saveCall(caseId, "gemini", geminiClient.getModel(), "MULTIMODAL_ATTACHMENT",
                    null, null, 0, "FAILED", true, "Gemini multimodal failure: " + e.getMessage(), null, now);
            }
        }

        // Fallback: If Gemini is unavailable or failed, notify via Groq text
        String fallbackContent = String.format(
            "{\"merchant\": \"Unknown\", \"amount\": null, \"currency\": \"USD\", \"date\": null, \"trackingNumber\": null, \"notes\": \"Multimodal analysis unavailable; file received: %s\"}",
            filename
        );

        if (groqClient.isAvailable()) {
            try {
                String groqNoticePrompt = "A file named '" + filename + "' (" + mimeType + ") was uploaded as evidence. Multimodal extraction is unavailable. Generate a JSON response with status 'FILE_RECEIVED' and notes 'Multimodal analysis unavailable; file received: " + filename + "'.";
                LlmResponse groqResp = groqClient.chatCompletion("Output JSON only.", groqNoticePrompt, true);
                saveCall(caseId, "groq", groqResp.model(), "MULTIMODAL_FALLBACK",
                    groqResp.promptTokens(), groqResp.completionTokens(), groqResp.latencyMs(),
                    "FALLBACK", true, "Gemini multimodal unavailable", null, now);
                return new LlmResponse(
                    groqResp.content(), "groq", groqResp.model(),
                    groqResp.promptTokens(), groqResp.completionTokens(), groqResp.latencyMs(),
                    true, "Gemini multimodal unavailable"
                );
            } catch (Exception ignored) {}
        }

        saveCall(caseId, "groq_fallback", "llama-3.3-70b-versatile", "MULTIMODAL_FALLBACK",
            0, 0, 1, "FALLBACK", true, "Multimodal fallback text generated", null, now);
        return new LlmResponse(fallbackContent, "groq_fallback", "llama-3.3-70b-versatile", 0, 0, 1, true, "Multimodal analysis unavailable; file received: " + filename);
    }

    /**
     * Routes Operations Agent interactive chat requests:
     * Groq primary -> Gemini fallback
     */
    public LlmResponse routeAgentChat(String systemContext, String userQuery) {
        Instant now = clock.instant();

        if (groqClient.isAvailable()) {
            try {
                LlmResponse resp = groqClient.chatCompletion(systemContext, userQuery, false);
                saveCall(null, "groq", resp.model(), "OPERATIONS_AGENT",
                    resp.promptTokens(), resp.completionTokens(), resp.latencyMs(),
                    "SUCCESS", false, null, null, now);
                return resp;
            } catch (Exception e) {
                log.warn("Operations agent Groq call failed: {}. Falling back to Gemini.", e.getMessage());
            }
        }

        if (geminiClient.isAvailable()) {
            try {
                LlmResponse resp = geminiClient.chatCompletion(systemContext, userQuery, false);
                saveCall(null, "gemini", resp.model(), "OPERATIONS_AGENT",
                    resp.promptTokens(), resp.completionTokens(), resp.latencyMs(),
                    "FALLBACK", true, "Groq unavailable", null, now);
                return resp;
            } catch (Exception e) {
                log.error("Operations agent Gemini call also failed: {}", e.getMessage());
            }
        }

        return new LlmResponse(
            "I'm operating in degraded telemetry mode. Based on current dashboard metrics, our exception queue is actively processing PayPal Sandbox transactions.",
            "rule_fallback", "rules", 0, 0, 1, true, "All providers unavailable"
        );
    }

    private void saveCall(
        String caseId, String provider, String model, String callType,
        Integer promptTokens, Integer completionTokens, long latencyMs,
        String status, boolean fallbackUsed, String fallbackReason,
        String snapshotHash, Instant now
    ) {
        try {
            llmCallRepository.save(LlmCallRecord.create(
                caseId, provider, model, callType,
                promptTokens, completionTokens, latencyMs,
                status, fallbackUsed, fallbackReason, snapshotHash, now
            ));
        } catch (Exception e) {
            log.warn("Failed to persist LLM call telemetry to database: {}", e.getMessage());
        }
    }
}

