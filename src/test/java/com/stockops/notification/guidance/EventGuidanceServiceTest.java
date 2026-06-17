package com.stockops.notification.guidance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stockops.ai.bedrock.BedrockAgentRuntimeClientAdapter;
import com.stockops.ai.bedrock.BedrockAiProperties;
import com.stockops.ai.provider.AiProviderFacade;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link EventGuidanceService} fallback behaviour (no live Knowledge Base).
 *
 * @author StockOps Team
 */
@ExtendWith(MockitoExtension.class)
class EventGuidanceServiceTest {

    @Mock
    private BedrockAgentRuntimeClientAdapter ragAdapter;

    @Mock
    private AiProviderFacade aiProviderFacade;

    @Mock
    private BedrockAiProperties bedrockProperties;

    @Test
    void usesFallbackWhenKnowledgeBaseNotConfigured() {
        when(bedrockProperties.isEnabled()).thenReturn(false);
        final EventGuidanceService service =
                new EventGuidanceService(ragAdapter, aiProviderFacade, bedrockProperties,
                        ObservationRegistry.NOOP);

        final EventGuidanceService.EventGuidance guidance =
                service.guidanceFor("TEMPERATURE_THRESHOLD", "CRITICAL");

        assertThat(guidance.source()).isEqualTo(EventGuidanceService.Source.FALLBACK);
        assertThat(guidance.text()).contains("냉동");
        // No KB/AI calls when not configured.
        verifyNoInteractions(ragAdapter);
        verifyNoInteractions(aiProviderFacade);
    }

    @Test
    void fallbackTextVariesByAlertType() {
        when(bedrockProperties.isEnabled()).thenReturn(false);
        final EventGuidanceService service =
                new EventGuidanceService(ragAdapter, aiProviderFacade, bedrockProperties,
                        ObservationRegistry.NOOP);

        assertThat(service.guidanceFor("HUMIDITY_THRESHOLD", "WARNING").text()).contains("제습");
        assertThat(service.guidanceFor("DOOR_OPEN_TOO_LONG", "WARNING").text()).contains("출입문");
    }
}
