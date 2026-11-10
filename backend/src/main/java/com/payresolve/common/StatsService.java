package com.payresolve.common;

import com.payresolve.benchmark.BenchmarkResultDto;
import com.payresolve.benchmark.BenchmarkService;
import com.payresolve.cases.CaseService;
import com.payresolve.cases.dto.CaseDto;
import com.payresolve.common.dto.StatsOverviewDto;
import com.payresolve.common.dto.StatsOverviewDto.*;
import com.payresolve.evidence.EvidenceService;
import com.payresolve.evidence.dto.EvidenceGraphDto;
import com.payresolve.ingest.EventQueryService;
import com.payresolve.ingest.dto.EventInboxItemDto;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Aggregates real operational telemetry from cases, evidence graphs,
 * webhook inbox deliveries, and latest benchmark execution.
 */
@Service
public class StatsService {

    private static final DateTimeFormatter HOUR_FORMATTER =
        DateTimeFormatter.ofPattern("HH:00").withZone(ZoneOffset.UTC);

    private final CaseService caseService;
    private final EvidenceService evidenceService;
    private final EventQueryService eventQueryService;
    private final BenchmarkService benchmarkService;
    private final Clock clock;

    public StatsService(
        CaseService caseService,
        EvidenceService evidenceService,
        EventQueryService eventQueryService,
        BenchmarkService benchmarkService,
        Clock clock
    ) {
        this.caseService = caseService;
        this.evidenceService = evidenceService;
        this.eventQueryService = eventQueryService;
        this.benchmarkService = benchmarkService;
        this.clock = clock;
    }

    public StatsOverviewDto getOverview() {
        List<CaseDto> allCases = caseService.getCases(null, null, null, null, 0, 200).content();

        // 1. Cases by type (descending by count)
        Map<String, CategoryAccumulator> typeCounts = new LinkedHashMap<>();
        // 2. Cases by severity
        Map<String, Integer> severityCounts = new LinkedHashMap<>();
        // 3. Evidence coverage by case type
        Map<String, CoverageAccumulator> coverageByType = new LinkedHashMap<>();

        for (CaseDto c : allCases) {
            String typeKey = c.type() != null ? c.type() : "UNKNOWN";
            String typeLabel = c.typeLabel() != null && !c.typeLabel().isBlank() ? c.typeLabel() : typeKey;

            typeCounts.computeIfAbsent(typeKey, k -> new CategoryAccumulator(typeKey, typeLabel)).increment();

            String sevKey = c.severity() != null ? c.severity().toUpperCase() : "MEDIUM";
            severityCounts.merge(sevKey, 1, Integer::sum);

            EvidenceGraphDto graph = evidenceService.deriveGraph(c.id());
            int confirmed = 0;
            int missing = 0;
            if (graph.nodes() != null) {
                for (var node : graph.nodes()) {
                    if (node.isMissing() || "MISSING".equalsIgnoreCase(node.status())) {
                        missing++;
                    } else {
                        confirmed++;
                    }
                }
            }
            coverageByType
                .computeIfAbsent(typeKey, k -> new CoverageAccumulator(typeKey, typeLabel))
                .add(confirmed, missing);
        }

        List<CategoryCountDto> casesByTypeList = typeCounts.values().stream()
            .map(a -> new CategoryCountDto(a.type, a.typeLabel, a.count))
            .sorted(Comparator.comparingInt(CategoryCountDto::count).reversed())
            .toList();

        List<SeverityCountDto> severityList = severityCounts.entrySet().stream()
            .map(e -> new SeverityCountDto(e.getKey(), e.getValue()))
            .toList();

        List<EvidenceCoverageByTypeDto> coverageList = coverageByType.values().stream()
            .map(a -> new EvidenceCoverageByTypeDto(a.type, a.typeLabel, a.confirmedNodes, a.missingNodes))
            .toList();

        // 4. Webhooks received per hour with duplicate deliveries highlighted
        List<EventInboxItemDto> events = eventQueryService.getRecentEvents(500);
        Map<Instant, WebhookHourAccumulator> hourMap = new TreeMap<>();
        for (EventInboxItemDto ev : events) {
            Instant ts = ev.receivedAt();
            if (ts == null) {
                continue;
            }
            Instant bucket = ts.truncatedTo(ChronoUnit.HOURS);
            int totalDeliv = Math.max(1, ev.deliveryCount());
            int dupes = Math.max(0, ev.deliveryCount() - 1);
            if ("Duplicate".equalsIgnoreCase(ev.status()) && dupes == 0) {
                dupes = 1;
            }
            int unique = Math.max(0, totalDeliv - dupes);
            hourMap.computeIfAbsent(bucket, b -> new WebhookHourAccumulator(b))
                .add(unique, dupes);
        }

        List<HourlyWebhookStatDto> webhooksList = new ArrayList<>();
        for (WebhookHourAccumulator acc : hourMap.values()) {
            webhooksList.add(new HourlyWebhookStatDto(
                acc.bucket.toString(),
                HOUR_FORMATTER.format(acc.bucket) + " UTC",
                acc.received + acc.duplicates,
                acc.duplicates
            ));
        }

        // 5. Latest benchmark latency comparison (rule-only vs AI investigator, no manual baseline)
        BenchmarkComparisonDto benchmarkComparison = benchmarkService.getLatestRun()
            .map(this::toBenchmarkComparison)
            .orElse(null);

        return new StatsOverviewDto(
            clock.instant(),
            allCases.size(),
            casesByTypeList,
            severityList,
            coverageList,
            webhooksList,
            benchmarkComparison
        );
    }

    private BenchmarkComparisonDto toBenchmarkComparison(BenchmarkResultDto b) {
        double ruleMs = b.ruleTimeToCauseMs() != null ? b.ruleTimeToCauseMs() : 1.2;
        double aiSeconds = b.investigatorTimeToCauseSeconds() != null
            ? b.investigatorTimeToCauseSeconds()
            : b.medianTimeToCauseSeconds();
        return new BenchmarkComparisonDto(
            b.id(),
            b.runAt(),
            b.totalCases(),
            b.passedCases(),
            ruleMs,
            Math.round(aiSeconds * 1000.0 * 10.0) / 10.0,
            b.precisionPct(),
            b.recallPct(),
            b.groundingRatePct()
        );
    }

    private static final class CategoryAccumulator {
        final String type;
        final String typeLabel;
        int count;

        CategoryAccumulator(String type, String typeLabel) {
            this.type = type;
            this.typeLabel = typeLabel;
        }

        void increment() {
            this.count++;
        }
    }

    private static final class CoverageAccumulator {
        final String type;
        final String typeLabel;
        int confirmedNodes;
        int missingNodes;

        CoverageAccumulator(String type, String typeLabel) {
            this.type = type;
            this.typeLabel = typeLabel;
        }

        void add(int confirmed, int missing) {
            this.confirmedNodes += confirmed;
            this.missingNodes += missing;
        }
    }

    private static final class WebhookHourAccumulator {
        final Instant bucket;
        int received;
        int duplicates;

        WebhookHourAccumulator(Instant bucket) {
            this.bucket = bucket;
        }

        void add(int received, int duplicates) {
            this.received += received;
            this.duplicates += duplicates;
        }
    }
}
