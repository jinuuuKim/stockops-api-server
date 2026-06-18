package com.stockops.controller;

import com.stockops.ai.bedrock.AiRagRateLimiter;
import com.stockops.ai.bedrock.BedrockAiFacade;
import com.stockops.ai.bedrock.BedrockConverseOrchestrator;
import com.stockops.ai.bedrock.KnowledgeBaseContextProvider;
import com.stockops.ai.bedrock.dto.AssistantJobCreatedResponse;
import com.stockops.ai.bedrock.dto.AssistantJobStatusResponse;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeRequest;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeResponse;
import com.stockops.ai.bedrock.dto.BedrockOpsSummaryResponse;
import com.stockops.ai.bedrock.job.AssistantJob;
import com.stockops.ai.bedrock.job.AssistantJobService;
import com.stockops.ai.bedrock.dto.BedrockRagQueryRequest;
import com.stockops.ai.bedrock.dto.BedrockRagQueryResponse;
import com.stockops.ai.bedrock.dto.BedrockRecommendationExplanationResponse;
import com.stockops.service.ai.AIRecommendationService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
    private final AiRagRateLimiter ragRateLimiter;
    private final BedrockConverseOrchestrator converseOrchestrator;
    private final KnowledgeBaseContextProvider knowledgeBaseContextProvider;
    private final AssistantJobService assistantJobService;

    public BedrockAiController(final AIRecommendationService recommendationService,
                               final BedrockAiFacade bedrockAiFacade,
                               final AiRagRateLimiter ragRateLimiter,
                               final BedrockConverseOrchestrator converseOrchestrator,
                               final KnowledgeBaseContextProvider knowledgeBaseContextProvider,
                               final AssistantJobService assistantJobService) {
        this.recommendationService = recommendationService;
        this.bedrockAiFacade = bedrockAiFacade;
        this.ragRateLimiter = ragRateLimiter;
        this.converseOrchestrator = converseOrchestrator;
        this.knowledgeBaseContextProvider = knowledgeBaseContextProvider;
        this.assistantJobService = assistantJobService;
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
            @Valid @RequestBody final BedrockRagQueryRequest request,
            final Authentication authentication) {
        // @PreAuthorize guarantees authentication is non-null in production;
        // null-check prevents NPE in standalone MockMvc tests without a security context.
        if (authentication != null) {
            ragRateLimiter.checkRagLimit(authentication.getName());
        }
        return ResponseEntity.ok(bedrockAiFacade.queryKnowledgeBase(request));
    }

    @PostMapping("/agent/invoke")
    @PreAuthorize("@permissionChecker.hasPermission('AI_RECOMMENDATION_READ')")
    public ResponseEntity<BedrockAgentInvokeResponse> invokeAgent(
            @Valid @RequestBody final BedrockAgentInvokeRequest request) {
        return ResponseEntity.ok(bedrockAiFacade.invokeAgent(request));
    }

    /**
     * StockOps AI assistant — direct Converse tool-use loop with KB grounding and guardrails.
     * This is the chosen orchestration path (not the managed Agent at {@code /agent/invoke}).
     *
     * @param request user message + optional scope/session
     * @param authentication current user (for rate limiting)
     * @return final assistant answer
     */
    @PostMapping("/assistant")
    @PreAuthorize("@permissionChecker.hasPermission('AI_RECOMMENDATION_READ')")
    public ResponseEntity<BedrockAgentInvokeResponse> assistant(
            @Valid @RequestBody final BedrockAgentInvokeRequest request,
            final Authentication authentication) {
        if (authentication != null) {
            ragRateLimiter.checkRagLimit(authentication.getName());
        }
        final String documentContext = knowledgeBaseContextProvider.retrieveContext(request.message());
        return ResponseEntity.ok(converseOrchestrator.converse(request, documentContext));
    }

    /**
     * Async variant of {@link #assistant}: starts the conversation on a worker thread and returns a
     * job id immediately, so the client polls {@link #getAssistantJob} instead of holding one long
     * request open (which fought the frontend axios / proxy read timeouts on slow tool-use turns).
     *
     * @param request        user message + optional scope/session
     * @param authentication current user (for rate limiting)
     * @return 202 Accepted with the job id to poll
     */
    @PostMapping("/assistant/jobs")
    @PreAuthorize("@permissionChecker.hasPermission('AI_RECOMMENDATION_READ')")
    public ResponseEntity<AssistantJobCreatedResponse> createAssistantJob(
            @Valid @RequestBody final BedrockAgentInvokeRequest request,
            final Authentication authentication) {
        if (authentication != null) {
            ragRateLimiter.checkRagLimit(authentication.getName());
        }
        final String jobId = assistantJobService.createJob(request);
        return ResponseEntity.accepted().body(new AssistantJobCreatedResponse(jobId));
    }

    /**
     * Returns the status/result of an async assistant job.
     *
     * @param jobId job identifier returned by {@link #createAssistantJob}
     * @return the job state, or 404 when unknown/expired
     */
    @GetMapping("/assistant/jobs/{jobId}")
    @PreAuthorize("@permissionChecker.hasPermission('AI_RECOMMENDATION_READ')")
    public ResponseEntity<AssistantJobStatusResponse> getAssistantJob(@PathVariable final String jobId) {
        final AssistantJob job = assistantJobService.getJob(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new AssistantJobStatusResponse(jobId, job.status(), job.result(), job.error()));
    }
}
