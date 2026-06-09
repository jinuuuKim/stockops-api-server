package com.stockops.controller;

import com.stockops.ai.chat.dto.AiChatRequest;
import com.stockops.ai.chat.dto.AiChatResponse;
import com.stockops.ai.provider.AiGenerationRequest;
import com.stockops.ai.provider.AiGenerationResponse;
import com.stockops.ai.provider.AiProviderFacade;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat")
public class AiChatController {

    private final AiProviderFacade providerFacade;

    public AiChatController(final AiProviderFacade providerFacade) {
        this.providerFacade = providerFacade;
    }

    @PostMapping("/messages")
    @PreAuthorize("@permissionChecker.hasPermission('AI_RECOMMENDATION_READ')")
    public ResponseEntity<AiChatResponse> sendMessage(
            @Valid @RequestBody final AiChatRequest request) {
        final AiGenerationResponse generation = providerFacade.generate(new AiGenerationRequest(
                "You are a helpful inventory operations assistant. Respond in Korean.",
                request.message(),
                "CHAT",
                true));
        return ResponseEntity.ok(new AiChatResponse(
                generation.text(),
                generation.provider(),
                generation.serviceStatus() != null ? generation.serviceStatus().name() : null,
                generation.fallbackUsed(),
                generation.fallbackNotice(),
                generation.serviceNotice(),
                generation.fallbackReason()));
    }
}
