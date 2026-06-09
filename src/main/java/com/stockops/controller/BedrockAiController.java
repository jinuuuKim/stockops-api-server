package com.stockops.controller;

import com.stockops.ai.bedrock.BedrockAiFacade;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeRequest;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeResponse;
import com.stockops.ai.bedrock.dto.BedrockOpsSummaryResponse;
import com.stockops.ai.bedrock.dto.BedrockRagQueryRequest;
import com.stockops.ai.bedrock.dto.BedrockRagQueryResponse;
import com.stockops.ai.bedrock.dto.BedrockRecommendationExplanationResponse;
import com.stockops.service.ai.AIRecommendationService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/bedrock")
public class BedrockAiController {

    private final AIRecommendationService recommendationService;
    private final BedrockAiFacade bedrockAiFacade;

    public BedrockAiController(final AIRecommendationService recommendationService,
                               final BedrockAiFacade bedrockAiFacade) {
        this.recommendationService = recommendationService;
        this.bedrockAiFacade = bedrockAiFacade;
    }

    @PostMapping("/recommendations/{recommendationId}/explain")
    @PreAuthorize("@permissionChecker.hasPermission('AI_RECOMMENDATION_READ')")
    public ResponseEntity<BedrockRecommendationExplanationResponse> explainRecommendation(
            @PathVariable final Long recommendationId) {
        final var recommendation = recommendationService.detailRecommendation(recommendationId);
        return ResponseEntity.ok(bedrockAiFacade.explainRecommendation(recommendation));
    }

    @GetMapping("/ops-summary")
    @PreAuthorize("@permissionChecker.hasPermission('AI_RECOMMENDATION_READ')")
    public ResponseEntity<BedrockOpsSummaryResponse> opsSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate businessDate,
            @RequestParam(required = false) final Long centerId,
            @RequestParam(required = false) final Long warehouseId) {
        return ResponseEntity.ok(bedrockAiFacade.summarizeOperations(businessDate, centerId, warehouseId));
    }

    @PostMapping("/rag/query")
    @PreAuthorize("@permissionChecker.hasPermission('AI_RECOMMENDATION_READ')")
    public ResponseEntity<BedrockRagQueryResponse> queryKnowledgeBase(
            @Valid @RequestBody final BedrockRagQueryRequest request) {
        return ResponseEntity.ok(bedrockAiFacade.queryKnowledgeBase(request));
    }

    @PostMapping("/agent/invoke")
    @PreAuthorize("@permissionChecker.hasPermission('AI_RECOMMENDATION_READ')")
    public ResponseEntity<BedrockAgentInvokeResponse> invokeAgent(
            @Valid @RequestBody final BedrockAgentInvokeRequest request) {
        return ResponseEntity.ok(bedrockAiFacade.invokeAgent(request));
    }
}
