package com.stockops.controller;

import com.stockops.dto.AIRecommendationDTO;
import com.stockops.entity.User;
import com.stockops.service.UserService;
import com.stockops.service.ai.AIRecommendationService;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for deterministic AI reorder recommendations.
 *
 * @author StockOps Team
 * @since 2.0
 */
@RestController
@RequestMapping("/api/v1/ai/recommendations")
public class AIRecommendationController {

    private final AIRecommendationService aiRecommendationService;
    private final UserService userService;

    /**
     * Returns scoped recommendation snapshots for the requested business date and filters.
     *
     * @param businessDate optional business date filter
     * @param centerId optional center filter
     * @param warehouseId optional warehouse filter
     * @param productId optional product filter
     * @param model optional forecast model selector (default: "statistical"; "prophet" delegates to Python AI service)
     * @return scoped recommendation payloads
     */
    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('AI_RECOMMENDATION_READ')")
    public List<AIRecommendationDTO> getRecommendations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate businessDate,
            @RequestParam(required = false) final Long centerId,
            @RequestParam(required = false) final Long warehouseId,
            @RequestParam(required = false) final Long productId,
            @RequestParam(required = false) final String model) {
        return aiRecommendationService.listRecommendations(businessDate, centerId, warehouseId, productId);
    }

    /**
     * Triggers on-demand recommendation generation for a business date using the specified model or provider.
     *
     * @param businessDate business date to generate recommendations for
     * @param model optional forecast model selector (default: "statistical")
     * @param provider optional external AI provider selector (a registered ExternalAiProvider id); takes precedence over {@code model}
     * @return 200 OK when generation completes
     */
    @PostMapping("/generate")
    @PreAuthorize("@permissionChecker.hasPermission('AI_RECOMMENDATION_APPROVE')")
    public ResponseEntity<Void> generateRecommendations(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate businessDate,
            @RequestParam(required = false) final String model,
            @RequestParam(required = false) final String provider) {
        final String effectiveModel = provider != null && !provider.isBlank() ? provider : model;
        final var forecastModel = aiRecommendationService.resolveForecastModel(effectiveModel);
        aiRecommendationService.generateRecommendationsForBusinessDate(businessDate, forecastModel);
        return ResponseEntity.ok().build();
    }

    /**
     * Approves one recommendation into a draft purchase order.
     *
     * @param recommendationId recommendation identifier
     * @param principal authenticated principal
     * @return approved recommendation payload with linked draft purchase-order data
     */
    @PostMapping("/{recommendationId}/approve")
    @PreAuthorize("@permissionChecker.hasPermission('AI_RECOMMENDATION_APPROVE')")
    public AIRecommendationDTO approveRecommendation(@PathVariable final Long recommendationId,
                                                     final Principal principal) {
        final User currentUser = userService.getUserByEmail(principal.getName());
        return aiRecommendationService.approveRecommendation(recommendationId, currentUser);
    }

    public AIRecommendationController(final AIRecommendationService aiRecommendationService, final UserService userService) {
        this.aiRecommendationService = aiRecommendationService;
        this.userService = userService;
    }
}
