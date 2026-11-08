package com.payresolve.benchmark;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/benchmark")
@Tag(name = "Benchmark", description = "Detection precision, recall, and grounding evaluation against seeded cases")
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    public BenchmarkController(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @GetMapping("/latest")
    @Operation(summary = "Get the latest benchmark evaluation run results")
    public ResponseEntity<BenchmarkResultDto> getLatestRun() {
        return benchmarkService.getLatestRun()
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.ok(null));
    }

    @PostMapping("/run")
    @Operation(summary = "Execute benchmark suite across 18 seeded test cases")
    public ResponseEntity<BenchmarkResultDto> runBenchmark() {
        BenchmarkResultDto result = benchmarkService.runBenchmark();
        return ResponseEntity.ok(result);
    }
}

