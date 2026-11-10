package com.payresolve.common;

import com.payresolve.common.dto.StatsOverviewDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only REST controller providing aggregated telemetry statistics for charts.
 */
@RestController
@RequestMapping("/stats")
@Tag(name = "Telemetry Stats", description = "Read-only aggregated queue, evidence, webhook, and benchmark statistics")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    @Operation(
        operationId = "getStatsOverview",
        summary = "Retrieve aggregated operational telemetry overview across cases, evidence graphs, webhooks, and benchmarks",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Aggregated telemetry overview retrieved successfully",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = StatsOverviewDto.class))
            )
        }
    )
    public ResponseEntity<StatsOverviewDto> getOverview() {
        return ResponseEntity.ok(statsService.getOverview());
    }
}
