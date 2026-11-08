package com.payresolve.evidence;

import com.payresolve.cases.CaseRecord;
import com.payresolve.cases.CaseSeverity;
import com.payresolve.cases.CaseService;
import com.payresolve.cases.CaseStatus;
import com.payresolve.cases.CaseType;
import com.payresolve.evidence.dto.EvidenceGraphDto;
import com.payresolve.evidence.dto.EvidenceGraphEdgeDto;
import com.payresolve.evidence.dto.EvidenceGraphNodeDto;
import com.payresolve.evidence.dto.EvidenceTimelineDto;
import com.payresolve.evidence.dto.EvidenceTimelineItemDto;
import com.payresolve.ingest.WebhookEventRepository;
import com.payresolve.shop.ShopOrder;
import com.payresolve.shop.ShopOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvidenceGraphDerivationTest {

    @Mock
    private CaseService caseService;

    @Mock
    private CaseEvidenceRepository evidenceRepository;

    @Mock
    private ShopOrderService shopOrderService;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private EvidenceService evidenceService;

    @BeforeEach
    void setUp() {
        evidenceService = new EvidenceService(
            caseService,
            evidenceRepository,
            shopOrderService,
            webhookEventRepository,
            clock,
            objectMapper
        );
    }

    @Test
    @DisplayName("PAID_NOT_MARKED: derives graph with missing PAID update and unfulfilled shipment")
    void testPaidNotMarkedGraphDerivation() {
        CaseRecord caseRecord = new CaseRecord(
            "PR-0142",
            CaseType.PAID_NOT_MARKED,
            CaseStatus.OPEN,
            CaseSeverity.HIGH,
            "PP-10482",
            "ORD-10482",
            8450L,
            "USD",
            clock.instant().minusSeconds(300),
            clock.instant().minusSeconds(300),
            null,
            "Payment captured but shop order remains unconfirmed."
        );

        ShopOrder shopOrder = new ShopOrder(
            "ORD-10482",
            "PP-10482",
            "AWAITING_CONFIRMATION",
            8450L,
            "USD",
            "Sarah Jenkins",
            clock.instant().minusSeconds(400),
            clock.instant().minusSeconds(400)
        );

        when(caseService.getCaseRecordById("PR-0142")).thenReturn(caseRecord);
        when(shopOrderService.findOrderById("ORD-10482")).thenReturn(Optional.of(shopOrder));
        when(shopOrderService.findPaymentByOrderId("ORD-10482")).thenReturn(Optional.empty());
        when(shopOrderService.getRefundsByOrderId("ORD-10482")).thenReturn(List.of());
        when(shopOrderService.getShipmentsByOrderId("ORD-10482")).thenReturn(List.of());
        when(webhookEventRepository.findAll(anyInt(), anyInt())).thenReturn(List.of());

        EvidenceGraphDto graph = evidenceService.deriveGraph("PR-0142");

        assertThat(graph.caseId()).isEqualTo("PR-0142");
        assertThat(graph.caseType()).isEqualTo("PAID_NOT_MARKED");
        assertThat(graph.missingNodesCount()).isGreaterThanOrEqualTo(1);

        // Check for missing PAID update node
        Optional<EvidenceGraphNodeDto> missingPaidNode = graph.nodes().stream()
            .filter(n -> n.id().equals("node-missing-paid-update"))
            .findFirst();
        assertThat(missingPaidNode).isPresent();
        assertThat(missingPaidNode.get().isMissing()).isTrue();
        assertThat(missingPaidNode.get().status()).isEqualTo("MISSING");
        assertThat(missingPaidNode.get().missingReason()).contains("never transitioned to PAID");

        // Check for missing shipment node
        Optional<EvidenceGraphNodeDto> missingShipmentNode = graph.nodes().stream()
            .filter(n -> n.id().equals("node-missing-shipment"))
            .findFirst();
        assertThat(missingShipmentNode).isPresent();
        assertThat(missingShipmentNode.get().isMissing()).isTrue();

        // Check for dashed edges connecting missing nodes
        List<EvidenceGraphEdgeDto> dashedEdges = graph.edges().stream()
            .filter(EvidenceGraphEdgeDto::isDashed)
            .toList();
        assertThat(dashedEdges).isNotEmpty();

        // Timeline verification
        EvidenceTimelineDto timeline = evidenceService.deriveTimeline("PR-0142");
        assertThat(timeline.caseId()).isEqualTo("PR-0142");
        assertThat(timeline.items()).isNotEmpty();

        boolean hasDiscrepancyItem = timeline.items().stream()
            .anyMatch(EvidenceTimelineItemDto::isDiscrepancy);
        assertThat(hasDiscrepancyItem).isTrue();
    }

    @Test
    @DisplayName("AMOUNT_MISMATCH: derives graph with missing amount parity node and dashed parity edge")
    void testAmountMismatchGraphDerivation() {
        CaseRecord caseRecord = new CaseRecord(
            "PR-0141",
            CaseType.AMOUNT_MISMATCH,
            CaseStatus.OPEN,
            CaseSeverity.HIGH,
            "PP-10479",
            "ORD-10479",
            7950L,
            "USD",
            clock.instant().minusSeconds(200),
            clock.instant().minusSeconds(200),
            null,
            "Captured amount differs from merchant shop order total."
        );

        ShopOrder shopOrder = new ShopOrder(
            "ORD-10479",
            "PP-10479",
            "PAID",
            7950L,
            "USD",
            "Michael Brown",
            clock.instant().minusSeconds(300),
            clock.instant().minusSeconds(300)
        );

        when(caseService.getCaseRecordById("PR-0141")).thenReturn(caseRecord);
        when(shopOrderService.findOrderById("ORD-10479")).thenReturn(Optional.of(shopOrder));
        when(shopOrderService.findPaymentByOrderId("ORD-10479")).thenReturn(Optional.empty());
        when(shopOrderService.getRefundsByOrderId("ORD-10479")).thenReturn(List.of());
        when(shopOrderService.getShipmentsByOrderId("ORD-10479")).thenReturn(List.of());
        when(webhookEventRepository.findAll(anyInt(), anyInt())).thenReturn(List.of());

        EvidenceGraphDto graph = evidenceService.deriveGraph("PR-0141");

        assertThat(graph.caseId()).isEqualTo("PR-0141");
        assertThat(graph.caseType()).isEqualTo("AMOUNT_MISMATCH");

        Optional<EvidenceGraphNodeDto> parityNode = graph.nodes().stream()
            .filter(n -> n.id().equals("node-missing-amount-parity"))
            .findFirst();
        assertThat(parityNode).isPresent();
        assertThat(parityNode.get().isMissing()).isTrue();
        assertThat(parityNode.get().missingReason()).contains("differs from merchant shop order total");

        boolean hasParityEdge = graph.edges().stream()
            .anyMatch(e -> e.isDashed() && e.type().equals("PARITY"));
        assertThat(hasParityEdge).isTrue();
    }

    @Test
    @DisplayName("DUPLICATE_EVENT: derives graph with duplicate delivery node and replay edge")
    void testDuplicateEventGraphDerivation() {
        CaseRecord caseRecord = new CaseRecord(
            "PR-0140",
            CaseType.DUPLICATE_EVENT,
            CaseStatus.OPEN,
            CaseSeverity.MEDIUM,
            "PP-10477",
            "ORD-10477",
            3200L,
            "USD",
            clock.instant().minusSeconds(150),
            clock.instant().minusSeconds(150),
            null,
            "Webhook delivered twice for same transaction."
        );

        ShopOrder shopOrder = new ShopOrder(
            "ORD-10477",
            "PP-10477",
            "PAID",
            3200L,
            "USD",
            "Emma Davis",
            clock.instant().minusSeconds(250),
            clock.instant().minusSeconds(250)
        );

        when(caseService.getCaseRecordById("PR-0140")).thenReturn(caseRecord);
        when(shopOrderService.findOrderById("ORD-10477")).thenReturn(Optional.of(shopOrder));
        when(shopOrderService.findPaymentByOrderId("ORD-10477")).thenReturn(Optional.empty());
        when(shopOrderService.getRefundsByOrderId("ORD-10477")).thenReturn(List.of());
        when(shopOrderService.getShipmentsByOrderId("ORD-10477")).thenReturn(List.of());
        when(webhookEventRepository.findAll(anyInt(), anyInt())).thenReturn(List.of());

        EvidenceGraphDto graph = evidenceService.deriveGraph("PR-0140");

        assertThat(graph.caseId()).isEqualTo("PR-0140");
        assertThat(graph.caseType()).isEqualTo("DUPLICATE_EVENT");

        Optional<EvidenceGraphNodeDto> dedupNode = graph.nodes().stream()
            .filter(n -> n.id().equals("node-missing-dedup-ack"))
            .findFirst();
        assertThat(dedupNode).isPresent();
        assertThat(dedupNode.get().label()).contains("Duplicate delivery");

        EvidenceTimelineDto timeline = evidenceService.deriveTimeline("PR-0140");
        assertThat(timeline.items()).anyMatch(i -> i.isDiscrepancy() && i.title().contains("Webhook"));
    }

    @Test
    @DisplayName("REFUND_MISMATCH: derives graph with missing refund sync node and dashed refund edge")
    void testRefundMismatchGraphDerivation() {
        CaseRecord caseRecord = new CaseRecord(
            "PR-0139",
            CaseType.REFUND_MISMATCH,
            CaseStatus.OPEN,
            CaseSeverity.MEDIUM,
            "PP-10470",
            "ORD-10470",
            1800L,
            "USD",
            clock.instant().minusSeconds(100),
            clock.instant().minusSeconds(100),
            null,
            "PayPal refund status differs from shop refund records."
        );

        ShopOrder shopOrder = new ShopOrder(
            "ORD-10470",
            "PP-10470",
            "PAID",
            1800L,
            "USD",
            "Lucas Miller",
            clock.instant().minusSeconds(200),
            clock.instant().minusSeconds(200)
        );

        when(caseService.getCaseRecordById("PR-0139")).thenReturn(caseRecord);
        when(shopOrderService.findOrderById("ORD-10470")).thenReturn(Optional.of(shopOrder));
        when(shopOrderService.findPaymentByOrderId("ORD-10470")).thenReturn(Optional.empty());
        when(shopOrderService.getRefundsByOrderId("ORD-10470")).thenReturn(List.of());
        when(shopOrderService.getShipmentsByOrderId("ORD-10470")).thenReturn(List.of());
        when(webhookEventRepository.findAll(anyInt(), anyInt())).thenReturn(List.of());

        EvidenceGraphDto graph = evidenceService.deriveGraph("PR-0139");

        assertThat(graph.caseId()).isEqualTo("PR-0139");
        assertThat(graph.caseType()).isEqualTo("REFUND_MISMATCH");

        Optional<EvidenceGraphNodeDto> refundNode = graph.nodes().stream()
            .filter(n -> n.id().equals("node-missing-refund-sync"))
            .findFirst();
        assertThat(refundNode).isPresent();
        assertThat(refundNode.get().isMissing()).isTrue();
        assertThat(refundNode.get().missingReason()).contains("PayPal refund status differs");
    }

    @Test
    @DisplayName("ORPHAN_CAPTURE: derives graph with missing merchant shop order node")
    void testOrphanCaptureGraphDerivation() {
        CaseRecord caseRecord = new CaseRecord(
            "PR-0138",
            CaseType.ORPHAN_CAPTURE,
            CaseStatus.OPEN,
            CaseSeverity.LOW,
            "CAP-998877",
            null,
            5620L,
            "USD",
            clock.instant().minusSeconds(80),
            clock.instant().minusSeconds(80),
            null,
            "Capture completed on PayPal with custom_id missing; no shop order exists."
        );

        when(caseService.getCaseRecordById("PR-0138")).thenReturn(caseRecord);
        when(shopOrderService.findOrderByPayPalOrderId("CAP-998877")).thenReturn(Optional.empty());
        when(webhookEventRepository.findAll(anyInt(), anyInt())).thenReturn(List.of());

        EvidenceGraphDto graph = evidenceService.deriveGraph("PR-0138");

        assertThat(graph.caseId()).isEqualTo("PR-0138");
        assertThat(graph.caseType()).isEqualTo("ORPHAN_CAPTURE");

        Optional<EvidenceGraphNodeDto> missingOrderNode = graph.nodes().stream()
            .filter(n -> n.id().equals("node-missing-shop-order"))
            .findFirst();
        assertThat(missingOrderNode).isPresent();
        assertThat(missingOrderNode.get().isMissing()).isTrue();
        assertThat(missingOrderNode.get().missingReason()).contains("no shop order exists");

        boolean hasOrphanEdge = graph.edges().stream()
            .anyMatch(e -> e.isDashed() && e.type().equals("ORPHAN"));
        assertThat(hasOrphanEdge).isTrue();
    }
}

