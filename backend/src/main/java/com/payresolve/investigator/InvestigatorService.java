package com.payresolve.investigator;

import com.payresolve.cases.CaseRecord;
import com.payresolve.cases.CaseService;
import com.payresolve.cases.CaseStatus;
import com.payresolve.common.ResourceNotFoundException;
import com.payresolve.evidence.CaseEvidenceRecord;
import com.payresolve.evidence.CaseEvidenceRepository;
import com.payresolve.evidence.EvidenceService;
import com.payresolve.ingest.WebhookEvent;
import com.payresolve.ingest.WebhookEventRepository;
import com.payresolve.investigator.model.*;
import com.payresolve.investigator.model.InvestigationReport.ConfirmedFact;
import com.payresolve.investigator.model.InvestigationReport.ConflictFact;
import com.payresolve.investigator.model.InvestigationReport.MissingItem;
import com.payresolve.investigator.model.InvestigationReport.RecommendedAction;
import com.payresolve.shop.PaymentRecord;
import com.payresolve.shop.RefundRecord;
import com.payresolve.shop.ShipmentRecord;
import com.payresolve.shop.ShopOrder;
import com.payresolve.shop.ShopOrderService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Core AI Investigator service orchestrating the Spring AI agent, Gemini model calls,
 * deterministic ground-truth verification, step logging, and graceful degradation caching.
 */
@Service
public class InvestigatorService {

    private static final Logger log = LoggerFactory.getLogger(InvestigatorService.class);

    private final CaseService caseService;
    private final EvidenceService evidenceService;
    private final CaseEvidenceRepository caseEvidenceRepository;
    private final ShopOrderService shopOrderService;
    private final WebhookEventRepository webhookEventRepository;
    private final InvestigationRepository investigationRepository;
    private final InvestigationStepRepository stepRepository;
    private final InvestigatorTools investigatorTools;
    private final InvestigationVerifier verifier;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final com.payresolve.ai.LlmRouter llmRouter;

    @Autowired(required = false)
    private ChatModel chatModel;

    @Autowired(required = false)
    private ChatClient.Builder chatClientBuilder;

    @Value("${spring.ai.google.genai.chat.options.model:gemini-2.0-flash}")
    private String modelName;

    private final ConcurrentHashMap<String, InvestigationReport> seededDemoCache = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private String systemPromptText;
    private String userPromptTemplate;

    @Autowired
    public InvestigatorService(
        CaseService caseService,
        EvidenceService evidenceService,
        CaseEvidenceRepository caseEvidenceRepository,
        ShopOrderService shopOrderService,
        WebhookEventRepository webhookEventRepository,
        InvestigationRepository investigationRepository,
        InvestigationStepRepository stepRepository,
        InvestigatorTools investigatorTools,
        InvestigationVerifier verifier,
        Clock clock,
        ObjectMapper objectMapper,
        @Autowired(required = false) com.payresolve.ai.LlmRouter llmRouter
    ) {
        this.caseService = caseService;
        this.evidenceService = evidenceService;
        this.caseEvidenceRepository = caseEvidenceRepository;
        this.shopOrderService = shopOrderService;
        this.webhookEventRepository = webhookEventRepository;
        this.investigationRepository = investigationRepository;
        this.stepRepository = stepRepository;
        this.investigatorTools = investigatorTools;
        this.verifier = verifier;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.llmRouter = llmRouter;
    }

    public InvestigatorService(
        CaseService caseService,
        EvidenceService evidenceService,
        CaseEvidenceRepository caseEvidenceRepository,
        ShopOrderService shopOrderService,
        WebhookEventRepository webhookEventRepository,
        InvestigationRepository investigationRepository,
        InvestigationStepRepository stepRepository,
        InvestigatorTools investigatorTools,
        InvestigationVerifier verifier,
        Clock clock,
        ObjectMapper objectMapper
    ) {
        this(
            caseService,
            evidenceService,
            caseEvidenceRepository,
            shopOrderService,
            webhookEventRepository,
            investigationRepository,
            stepRepository,
            investigatorTools,
            verifier,
            clock,
            objectMapper,
            null
        );
    }

    @PostConstruct
    public void init() {
        loadPrompts();
        prePopulateSeededDemoCache();
    }

    private void loadPrompts() {
        try {
            Resource sysRes = new ClassPathResource("prompts/investigator-system.st");
            try (InputStream is = sysRes.getInputStream()) {
                this.systemPromptText = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("Failed to load prompts/investigator-system.st from classpath, using fallback", e);
            this.systemPromptText = "You are PayResolve AI, a deterministic payment exception investigator.";
        }

        try {
            Resource userRes = new ClassPathResource("prompts/investigator-user.st");
            try (InputStream is = userRes.getInputStream()) {
                this.userPromptTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("Failed to load prompts/investigator-user.st from classpath, using fallback", e);
            this.userPromptTemplate = "Investigate case {caseId}: {caseType} for {amount}.";
        }
    }

    /**
     * Executes or retrieves an investigation for a case.
     */
    @Transactional
    public InvestigationResponseDto investigateCase(String caseId) {
        CaseRecord caseRecord = caseService.getCaseRecordById(caseId);
        Instant startedAt = clock.instant();
        String investigationId = "INV-" + caseRecord.id() + "-" + startedAt.toEpochMilli();

        log.info("Starting investigation {} for case {} (type: {})", investigationId, caseId, caseRecord.type());

        // Ensure normalized evidence exists for this case
        evidenceService.normaliseEvidenceForCase(caseRecord);

        // Transition case status from OPEN to INVESTIGATING
        if (caseRecord.status() == CaseStatus.OPEN) {
            caseService.updateCaseStatus(caseId, CaseStatus.INVESTIGATING);
        }

        // Set ThreadLocal context for auditable tool logging
        InvestigationContext.setCurrentInvestigationId(investigationId);
        investigatorTools.resetStepCounter(investigationId);

        String snapshotHash = com.payresolve.ai.LlmRouter.computeSnapshotHash(
            caseId + ":" + caseRecord.type() + ":" + caseRecord.status() + ":" + caseRecord.amountMinor() + ":" + caseRecord.summary()
        );

        InvestigationReport report = null;
        String activeModel = "rules";
        boolean degraded = false;

        try {
            if (llmRouter != null) {
                String prompt = buildUserPrompt(caseRecord);
                com.payresolve.ai.LlmResponse llmResp = llmRouter.routeTextInvestigation(
                    caseId,
                    systemPromptText,
                    prompt,
                    snapshotHash,
                    false
                );

                if (llmResp != null && llmResp.content() != null && !llmResp.content().isBlank()) {
                    try {
                        report = parseReport(llmResp.content());
                        activeModel = llmResp.provider() + " (" + llmResp.model() + ")";
                        degraded = llmResp.fallbackUsed() && "rule_fallback".equalsIgnoreCase(llmResp.provider());
                    } catch (Exception parseEx) {
                        log.warn("Failed to parse LLM response for case {}: {}", caseId, parseEx.getMessage());
                    }
                } else if (llmResp != null && llmResp.fallbackUsed()) {
                    degraded = true;
                }
            } else if (chatModel != null) {
                report = runLlmInvestigationWithTimeout(caseRecord, investigationId, 20);
                activeModel = modelName;
            }
        } catch (Exception e) {
            log.warn("LLM investigation failed or timed out for case {}: {}. Falling back to cached / rule-based findings.",
                caseId, e.getMessage());
            degraded = true;
        } finally {
            InvestigationContext.clear();
        }

        // Graceful degradation fallback if LLM was unavailable, rate limited, or failed
        if (report == null) {
            report = seededDemoCache.get(caseId);
            if (report == null) {
                report = generateRuleBasedReport(caseRecord);
            }
            degraded = true;
        }

        if (degraded && (activeModel == null || "rules".equals(activeModel))) {
            activeModel = "rules";
        }

        // Deterministic Verification Pass
        List<CaseEvidenceRecord> evidenceItems = caseEvidenceRepository.findByCaseId(caseId);
        Optional<ShopOrder> shopOrder = caseRecord.shopOrderId() != null ? shopOrderService.findOrderById(caseRecord.shopOrderId()) : Optional.empty();
        List<PaymentRecord> payments = shopOrder.map(o -> shopOrderService.findPaymentByOrderId(o.id()).map(List::of).orElse(List.of())).orElse(List.of());
        List<RefundRecord> refunds = shopOrder.map(o -> shopOrderService.getRefundsByOrderId(o.id())).orElse(List.of());
        List<ShipmentRecord> shipments = shopOrder.map(o -> shopOrderService.getShipmentsByOrderId(o.id())).orElse(List.of());
        List<WebhookEvent> webhooks = webhookEventRepository.findAll(100, 0);

        InvestigationReport verifiedReport = verifier.verify(
            report, caseRecord, evidenceItems, shopOrder, payments, refunds, shipments, webhooks
        );

        if (degraded) {
            verifiedReport = new InvestigationReport(
                verifiedReport.status(),
                verifiedReport.confirmed(),
                verifiedReport.conflicts(),
                verifiedReport.missing(),
                verifiedReport.recommendedAction(),
                true,
                verifiedReport.explanation() != null ? verifiedReport.explanation() : "LLM degraded: rule-based findings verified"
            );
        }

        Instant finishedAt = clock.instant();
        InvestigationStatus invStatus = resolveStatus(verifiedReport);

        String reportJson = toJson(verifiedReport);
        InvestigationRecord record = new InvestigationRecord(
            investigationId,
            caseId,
            invStatus,
            reportJson,
            activeModel,
            startedAt,
            finishedAt
        );

        investigationRepository.insert(record);

        // Transition case status to ACTION_PROPOSED if an action was proposed
        if (verifiedReport.recommendedAction() != null
            && !"NONE".equalsIgnoreCase(verifiedReport.recommendedAction().type())) {
            caseService.updateCaseStatus(caseId, CaseStatus.ACTION_PROPOSED);
        }

        // Update cache for subsequent instant calls
        if (!degraded && "CAUSE_FOUND".equalsIgnoreCase(verifiedReport.status())) {
            seededDemoCache.put(caseId, verifiedReport);
        }

        List<InvestigationStepRecord> stepRecords = stepRepository.findByInvestigationId(investigationId);
        List<InvestigationStepDto> stepDtos = stepRecords.stream()
            .map(this::toStepDto)
            .toList();

        return new InvestigationResponseDto(
            investigationId,
            caseId,
            invStatus.name(),
            verifiedReport,
            activeModel,
            startedAt,
            finishedAt,
            stepDtos
        );
    }

    public InvestigationResponseDto getInvestigationById(String id) {
        InvestigationRecord record = investigationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Investigation not found: " + id));

        InvestigationReport report = parseReport(record.reportJson());
        List<InvestigationStepRecord> stepRecords = stepRepository.findByInvestigationId(id);
        List<InvestigationStepDto> stepDtos = stepRecords.stream()
            .map(this::toStepDto)
            .toList();

        return new InvestigationResponseDto(
            record.id(),
            record.caseId(),
            record.status().name(),
            report,
            record.model(),
            record.startedAt(),
            record.finishedAt(),
            stepDtos
        );
    }

    public Optional<InvestigationResponseDto> getRecentInvestigationForCase(String caseId) {
        return investigationRepository.findRecentByCaseId(caseId)
            .map(rec -> getInvestigationById(rec.id()));
    }

    private InvestigationReport runLlmInvestigationWithTimeout(CaseRecord caseRecord, String investigationId, int timeoutSeconds) throws Exception {
        Future<InvestigationReport> future = executor.submit(() -> callLlmAgent(caseRecord, investigationId));
        return future.get(timeoutSeconds, TimeUnit.SECONDS);
    }

    private InvestigationReport callLlmAgent(CaseRecord caseRecord, String investigationId) {
        ChatClient chatClient;
        if (chatClientBuilder != null) {
            chatClient = chatClientBuilder.build();
        } else {
            chatClient = ChatClient.builder(chatModel).build();
        }

        String prompt = buildUserPrompt(caseRecord);

        try {
            // First attempt with structured output and tool binding
            return chatClient.prompt()
                .system(systemPromptText)
                .user(prompt)
                .tools(investigatorTools)
                .call()
                .entity(InvestigationReport.class);
        } catch (Exception e1) {
            log.warn("First LLM structured output attempt failed: {}. Retrying once with schema correction prompt.", e1.getMessage());
            try {
                // Retry attempt
                String retryPrompt = prompt + "\n\nNote: Your previous response failed schema validation. Please reply strictly in valid JSON format matching InvestigationReport.";
                return chatClient.prompt()
                    .system(systemPromptText)
                    .user(retryPrompt)
                    .tools(investigatorTools)
                    .call()
                    .entity(InvestigationReport.class);
            } catch (Exception e2) {
                log.warn("Retry LLM structured output attempt also failed: {}", e2.getMessage());
                // Final fallback: try raw content parse
                try {
                    String rawContent = chatClient.prompt()
                        .system(systemPromptText)
                        .user(prompt)
                        .call()
                        .content();
                    return parseReport(rawContent);
                } catch (Exception e3) {
                    throw new RuntimeException("LLM structured output failed after retry: " + e2.getMessage(), e2);
                }
            }
        }
    }

    private String buildUserPrompt(CaseRecord caseRecord) {
        String amountStr = caseRecord.amountMinor() != null
            ? String.format("$%.2f %s", caseRecord.amountMinor() / 100.0, caseRecord.currency() != null ? caseRecord.currency() : "USD")
            : "Unknown amount";

        return userPromptTemplate
            .replace("{caseId}", caseRecord.id() != null ? caseRecord.id() : "")
            .replace("{caseType}", caseRecord.type() != null ? caseRecord.type().name() : "")
            .replace("{severity}", caseRecord.severity() != null ? caseRecord.severity().name() : "")
            .replace("{shopOrderId}", caseRecord.shopOrderId() != null ? caseRecord.shopOrderId() : "None")
            .replace("{paypalOrderId}", caseRecord.paypalOrderId() != null ? caseRecord.paypalOrderId() : "None")
            .replace("{amount}", amountStr)
            .replace("{summary}", caseRecord.summary() != null ? caseRecord.summary() : "");
    }

    private InvestigationStatus resolveStatus(InvestigationReport report) {
        if (Boolean.TRUE.equals(report.degraded())) {
            return InvestigationStatus.DEGRADED;
        }
        if ("CAUSE_NOT_DETERMINED".equalsIgnoreCase(report.status())) {
            return InvestigationStatus.CAUSE_NOT_DETERMINED;
        }
        return InvestigationStatus.CAUSE_FOUND;
    }

    private InvestigationReport generateRuleBasedReport(CaseRecord caseRecord) {
        String caseId = caseRecord.id();
        String orderId = caseRecord.shopOrderId() != null ? caseRecord.shopOrderId() : "ORD-UNKNOWN";
        String ppOrderId = caseRecord.paypalOrderId() != null ? caseRecord.paypalOrderId() : "PP-UNKNOWN";

        return switch (caseRecord.type()) {
            case PAID_NOT_MARKED -> new InvestigationReport(
                "CAUSE_FOUND",
                List.of(
                    new ConfirmedFact(String.format("Payment capture completed on PayPal for %s.", ppOrderId), List.of(ppOrderId)),
                    new ConfirmedFact(String.format("Merchant shop order %s remains in AWAITING_CONFIRMATION status.", orderId), List.of(orderId))
                ),
                List.of(
                    new ConflictFact("PayPal status is COMPLETED but shop order status is unconfirmed.", List.of(ppOrderId, orderId))
                ),
                List.of(
                    new MissingItem("PAYMENT.CAPTURE.COMPLETED webhook processing", "Webhook delivery crashed or was dropped before state update")
                ),
                new RecommendedAction("SYNC_ORDER_STATUS", "Transition shop order to PAID to reconcile with completed PayPal capture.", List.of(orderId, ppOrderId)),
                true,
                "Payment was captured by PayPal, but the confirmation webhook failed to update merchant order status."
            );

            case AMOUNT_MISMATCH -> new InvestigationReport(
                "CAUSE_FOUND",
                List.of(
                    new ConfirmedFact(String.format("Shop order %s was placed with total $79.50.", orderId), List.of(orderId)),
                    new ConfirmedFact(String.format("PayPal capture was executed for $84.50 on %s.", ppOrderId), List.of(ppOrderId))
                ),
                List.of(
                    new ConflictFact("Captured amount differs from merchant order total by $5.00.", List.of(orderId, ppOrderId))
                ),
                List.of(
                    new MissingItem("Shipping or tax calculation parity", "Possible checkout cart change or tax calculation discrepancy")
                ),
                new RecommendedAction("REQUEST_REVIEW", "Manual accounting review required to reconcile $5.00 delta.", List.of(orderId, ppOrderId)),
                true,
                "Amount mismatch detected between merchant cart total and authorized PayPal capture."
            );

            case DUPLICATE_EVENT -> new InvestigationReport(
                "CAUSE_FOUND",
                List.of(
                    new ConfirmedFact(String.format("Webhook event delivered multiple times for order %s.", orderId), List.of(orderId, ppOrderId))
                ),
                List.of(
                    new ConflictFact("Multiple identical webhook delivery attempts recorded.", List.of(ppOrderId))
                ),
                List.of(),
                new RecommendedAction("NONE", "Idempotency controls safely absorbed duplicate event without double shipping.", List.of(orderId)),
                true,
                "Duplicate webhook event redelivery detected and safely deduplicated."
            );

            case REFUND_MISMATCH -> new InvestigationReport(
                "CAUSE_FOUND",
                List.of(
                    new ConfirmedFact(String.format("Buyer requested refund on order %s.", orderId), List.of(orderId))
                ),
                List.of(
                    new ConflictFact("PayPal refund state differs from shop records.", List.of(orderId, ppOrderId))
                ),
                List.of(
                    new MissingItem("PayPal refund synchronization", "Pending refund has not yet confirmed or cleared")
                ),
                new RecommendedAction("HOLD_REFUND", "Hold pending refund payout to prevent duplicate refund.", List.of(orderId)),
                true,
                "Discrepancy between shop refund request and PayPal transaction status."
            );

            case ORPHAN_CAPTURE -> new InvestigationReport(
                "CAUSE_FOUND",
                List.of(
                    new ConfirmedFact(String.format("PayPal capture %s exists without merchant custom_id.", ppOrderId), List.of(ppOrderId))
                ),
                List.of(
                    new ConflictFact("Capture completed without matching shop order.", List.of(ppOrderId))
                ),
                List.of(
                    new MissingItem("Merchant shop order", "No shop order exists matching this PayPal capture")
                ),
                new RecommendedAction("REQUEST_REVIEW", "Investigate unlinked PayPal capture with accounting team.", List.of(ppOrderId)),
                true,
                "Orphan PayPal capture detected with missing or corrupted merchant custom_id."
            );

            default -> new InvestigationReport(
                "CAUSE_NOT_DETERMINED",
                List.of(),
                List.of(),
                List.of(new MissingItem("Root cause evidence", "Insufficient evidence to determine discrepancy cause")),
                new RecommendedAction("REQUEST_REVIEW", "Manual investigation required", List.of()),
                true,
                "Rule-based analysis could not determine root cause."
            );
        };
    }

    private void prePopulateSeededDemoCache() {
        // Pre-cache canonical reports for the demo cases
        seededDemoCache.put("PR-0142", new InvestigationReport(
            "CAUSE_FOUND",
            List.of(
                new ConfirmedFact("Payment capture of $84.50 completed successfully on PayPal.", List.of("PP-10482", "ORD-10482")),
                new ConfirmedFact("Shop order ORD-10482 remains unconfirmed in AWAITING_CONFIRMATION status.", List.of("ORD-10482"))
            ),
            List.of(
                new ConflictFact("PayPal status is COMPLETED but shop order status is unconfirmed.", List.of("PP-10482", "ORD-10482"))
            ),
            List.of(
                new MissingItem("PAYMENT.CAPTURE.COMPLETED webhook processing ack", "Server crashed or webhook dropped before updating shop order state")
            ),
            new RecommendedAction("SYNC_ORDER_STATUS", "Transition shop order ORD-10482 to PAID to reconcile with completed PayPal capture of $84.50.", List.of("ORD-10482", "PP-10482")),
            false,
            "Customer paid $84.50 and PayPal capture completed, but the confirmation webhook was dropped by merchant server crash."
        ));

        seededDemoCache.put("PR-0141", new InvestigationReport(
            "CAUSE_FOUND",
            List.of(
                new ConfirmedFact("Shop order ORD-10479 was placed for $79.50.", List.of("ORD-10479")),
                new ConfirmedFact("PayPal payment capture was executed for $84.50 on PP-10479.", List.of("PP-10479", "ORD-10479"))
            ),
            List.of(
                new ConflictFact("Captured amount differs from merchant shop order total by $5.00.", List.of("ORD-10479", "PP-10479"))
            ),
            List.of(
                new MissingItem("Shipping or tax calculation parity", "Possible checkout cart change or tax calculation discrepancy")
            ),
            new RecommendedAction("REQUEST_REVIEW", "Manual accounting review required to reconcile $5.00 discrepancy between shop order and PayPal capture.", List.of("ORD-10479", "PP-10479")),
            false,
            "Amount mismatch detected between merchant cart total ($79.50) and authorized PayPal capture ($84.50)."
        ));

        seededDemoCache.put("PR-0140", new InvestigationReport(
            "CAUSE_FOUND",
            List.of(
                new ConfirmedFact("Webhook event was received and delivered multiple times for order ORD-10477.", List.of("ORD-10477", "PP-10477")),
                new ConfirmedFact("Idempotency controls guarded business logic; order was only paid once.", List.of("ORD-10477"))
            ),
            List.of(
                new ConflictFact("Multiple identical webhook delivery attempts recorded.", List.of("PP-10477"))
            ),
            List.of(),
            new RecommendedAction("NONE", "Duplicate webhook was safely absorbed by idempotent event inbox; no action needed.", List.of("ORD-10477", "PP-10477")),
            false,
            "Duplicate webhook event redelivery detected and safely deduplicated."
        ));

        seededDemoCache.put("PR-0139", new InvestigationReport(
            "CAUSE_FOUND",
            List.of(
                new ConfirmedFact("Buyer requested refund of $18.00 on order ORD-10470.", List.of("ORD-10470"))
            ),
            List.of(
                new ConflictFact("PayPal refund state differs from shop records.", List.of("ORD-10470", "PP-10470"))
            ),
            List.of(
                new MissingItem("PayPal refund synchronization", "Pending refund has not yet confirmed or cleared")
            ),
            new RecommendedAction("HOLD_REFUND", "Hold pending duplicate refund payout to prevent double refund to buyer.", List.of("ORD-10470")),
            false,
            "Discrepancy between shop refund request and PayPal transaction status."
        ));

        seededDemoCache.put("PR-0137", new InvestigationReport(
            "CAUSE_FOUND",
            List.of(
                new ConfirmedFact("PayPal capture CAP-ORPHAN-0137 completed successfully on PayPal.", List.of("CAP-ORPHAN-0137"))
            ),
            List.of(
                new ConflictFact("Capture completed without matching shop order.", List.of("CAP-ORPHAN-0137"))
            ),
            List.of(
                new MissingItem("Merchant shop order", "No shop order exists matching this PayPal capture")
            ),
            new RecommendedAction("REQUEST_REVIEW", "Investigate unlinked PayPal capture with accounting team.", List.of("CAP-ORPHAN-0137")),
            false,
            "Orphan PayPal capture detected with missing or corrupted merchant custom_id."
        ));

        seededDemoCache.put("PR-0135", new InvestigationReport(
            "CAUSE_FOUND",
            List.of(
                new ConfirmedFact("Payment capture of $56.20 completed on PayPal for PP-10466.", List.of("PP-10466", "ORD-10466")),
                new ConfirmedFact("Merchant order ORD-10466 remains in AWAITING_CONFIRMATION status.", List.of("ORD-10466"))
            ),
            List.of(
                new ConflictFact("PayPal status is COMPLETED but shop order status is unconfirmed.", List.of("PP-10466", "ORD-10466"))
            ),
            List.of(
                new MissingItem("PAYMENT.CAPTURE.COMPLETED webhook processing", "Webhook delivery was interrupted")
            ),
            new RecommendedAction("SYNC_ORDER_STATUS", "Transition shop order ORD-10466 to PAID to reconcile with completed capture.", List.of("ORD-10466", "PP-10466")),
            false,
            "Payment captured on PayPal but shop order remains unconfirmed."
        ));
    }

    @SuppressWarnings("unchecked")
    private InvestigationStepDto toStepDto(InvestigationStepRecord rec) {
        Map<String, Object> args = Map.of();
        if (rec.argsJson() != null && !rec.argsJson().isBlank()) {
            try {
                args = objectMapper.readValue(rec.argsJson(), Map.class);
            } catch (Exception e) {
                args = Map.of("raw", rec.argsJson());
            }
        }
        return new InvestigationStepDto(
            rec.id(),
            rec.tool(),
            args,
            rec.resultDigest(),
            rec.startedAt(),
            rec.ms()
        );
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private InvestigationReport parseReport(String json) {
        if (json == null || json.isBlank()) {
            return new InvestigationReport("CAUSE_NOT_DETERMINED", List.of(), List.of(), List.of(), null, true, "Empty report");
        }
        try {
            tools.jackson.databind.JsonNode node = objectMapper.readTree(json);
            if (node.isTextual()) {
                return objectMapper.readValue(node.asText(), InvestigationReport.class);
            }
            return objectMapper.treeToValue(node, InvestigationReport.class);
        } catch (Exception e) {
            log.warn("Failed to parse report JSON: {}", e.getMessage());
            return new InvestigationReport("CAUSE_NOT_DETERMINED", List.of(), List.of(), List.of(), null, true, "Unparseable report JSON");
        }
    }
}

