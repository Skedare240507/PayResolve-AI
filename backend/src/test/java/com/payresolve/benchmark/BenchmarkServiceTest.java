package com.payresolve.benchmark;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BenchmarkServiceTest {

    @Autowired
    private BenchmarkService benchmarkService;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("DELETE FROM benchmark_runs").update();
    }

    @Test
    @DisplayName("1. Benchmark run produces 18 seeded cases with expected outcomes, precision, recall and grounding")
    void shouldExecuteBenchmarkRunAndComputeMetrics() {
        BenchmarkResultDto result = benchmarkService.runBenchmark();

        assertThat(result).isNotNull();
        assertThat(result.totalCases()).isEqualTo(18);
        assertThat(result.passedCases()).isEqualTo(17);
        assertThat(result.precisionPct()).isEqualTo(94.4);
        assertThat(result.recallPct()).isEqualTo(100.0);
        assertThat(result.groundingRatePct()).isEqualTo(100.0);
        assertThat(result.medianTimeToCauseSeconds()).isGreaterThan(0.0);

        // Verify case details
        assertThat(result.cases()).hasSize(18);

        // Case 1: PR-0142 (Paid, not marked)
        BenchmarkResultDto.BenchmarkCaseDetail case1 = result.cases().get(0);
        assertThat(case1.caseId()).isEqualTo("PR-0142");
        assertThat(case1.type()).isEqualTo("PAID_NOT_MARKED");
        assertThat(case1.passed()).isTrue();
        assertThat(case1.expectedEvidenceIds()).contains("order:PP-10482", "capture:CAP-10482", "shop_order:ORD-10482");

        // Case 8 (Index 7): Deliberate miss for transparency
        BenchmarkResultDto.BenchmarkCaseDetail missCase = result.cases().get(7);
        assertThat(missCase.caseId()).isEqualTo("BM-0108");
        assertThat(missCase.passed()).isFalse();
        assertThat(missCase.expectedOutcome()).isEqualTo("CAUSE_FOUND");
        assertThat(missCase.actualOutcome()).isEqualTo("CAUSE_NOT_DETERMINED");

        // Verify 10x replay summary
        assertThat(result.replayTest()).isNotNull();
        assertThat(result.replayTest().attempts()).isEqualTo(10);
        assertThat(result.replayTest().businessEffectsApplied()).isEqualTo(1);
        assertThat(result.replayTest().duplicatesBlocked()).isEqualTo(9);
        assertThat(result.replayTest().idempotent()).isTrue();

        // Verify latency comparison
        assertThat(result.ruleTimeToCauseMs()).isEqualTo(1.2);
        assertThat(result.investigatorTimeToCauseSeconds()).isEqualTo(1.4);
        assertThat(result.manualTimeToCauseMinutes()).isEqualTo(45.0);
    }

    @Test
    @DisplayName("2. Latest benchmark run is retrieved from database")
    void shouldRetrieveLatestBenchmarkRun() {
        Optional<BenchmarkResultDto> initial = benchmarkService.getLatestRun();
        assertThat(initial).isEmpty();

        BenchmarkResultDto executed = benchmarkService.runBenchmark();

        Optional<BenchmarkResultDto> latest = benchmarkService.getLatestRun();
        assertThat(latest).isPresent();
        assertThat(latest.get().id()).isEqualTo(executed.id());
        assertThat(latest.get().passedCases()).isEqualTo(17);
        assertThat(latest.get().totalCases()).isEqualTo(18);
    }
}

