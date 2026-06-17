package com.stockops.notification.guidance;

import com.stockops.ai.bedrock.BedrockAgentRuntimeClientAdapter;
import com.stockops.ai.bedrock.BedrockAiProperties;
import com.stockops.ai.bedrock.dto.BedrockRagQueryRequest;
import com.stockops.ai.bedrock.dto.BedrockRagQueryResponse;
import com.stockops.ai.provider.AiGenerationRequest;
import com.stockops.ai.provider.AiGenerationResponse;
import com.stockops.ai.provider.AiProviderFacade;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Produces Korean remediation guidance ("상세 조치안내") for an event alert.
 *
 * <p>Two-pass design: (1) {@link BedrockAgentRuntimeClientAdapter#retrieveAndGenerate} grounds the
 * answer in the curated Knowledge Base, then (2) {@link AiProviderFacade#generate} re-formats it
 * into our card shape so a drifting KB answer cannot break the message. When the Knowledge Base is
 * not configured (e.g. local/demo), or any AI call is unavailable, a static per-alert-type template
 * is returned so the notification always carries usable guidance.
 *
 * <p>Results are cached by {@code (alertType, severity)} — guidance depends on the kind and level of
 * the event, not on the specific reading — to keep Bedrock calls, latency, and cost low.
 *
 * @author StockOps Team
 * @since 2.3
 */
@Service
public class EventGuidanceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventGuidanceService.class);

    /** Source of the returned guidance, recorded for delivery auditing. */
    public enum Source { KNOWLEDGE_BASE, AI_FORMATTED, FALLBACK }

    /** Guidance text plus its provenance (KB citation or fallback marker) for logging. */
    public record EventGuidance(String text, Source source, String citation) {
    }

    private static final String FORMAT_SYSTEM_PROMPT = """
            너는 물류창고 운영 알림의 '조치안내'를 작성하는 어시스턴트다.
            주어진 근거를 바탕으로 운영자가 즉시 취할 수 있는 조치를 한국어로 작성하라.
            번호를 매긴 3~5개의 간결한 단계로만 정리하고, 근거에 없는 내용은 일반적 안전 조치 범위로만 보강하라.
            전체 250자 이내, 군더더기 없이.
            """;

    private final BedrockAgentRuntimeClientAdapter ragAdapter;
    private final AiProviderFacade aiProviderFacade;
    private final BedrockAiProperties bedrockProperties;
    private final ObservationRegistry observationRegistry;

    public EventGuidanceService(final BedrockAgentRuntimeClientAdapter ragAdapter,
                                final AiProviderFacade aiProviderFacade,
                                final BedrockAiProperties bedrockProperties,
                                final ObservationRegistry observationRegistry) {
        this.ragAdapter = ragAdapter;
        this.aiProviderFacade = aiProviderFacade;
        this.bedrockProperties = bedrockProperties;
        this.observationRegistry = observationRegistry;
    }

    /**
     * Returns remediation guidance for the given alert type and severity. Cached by the pair.
     *
     * @param alertType raw alert type (e.g. "TEMPERATURE_THRESHOLD"); may be null
     * @param severity  severity name (e.g. "CRITICAL"); may be null
     * @return guidance text + provenance, never null
     */
    @Cacheable(cacheNames = "ai::event-guidance", key = "(#alertType ?: 'NONE') + '|' + (#severity ?: 'NONE')")
    public EventGuidance guidanceFor(final String alertType, final String severity) {
        // Manual span: only runs on a cache miss (the @Cacheable proxy short-circuits hits), so it
        // measures the actual KB/AI generation latency, tagged with the type/severity and the source
        // (KNOWLEDGE_BASE / AI_FORMATTED / FALLBACK) of the produced guidance.
        final Observation observation = Observation.createNotStarted(
                        "notification.event_guidance", observationRegistry)
                .contextualName("event-guidance")
                .lowCardinalityKeyValue("alert_type", alertType == null ? "unknown" : alertType)
                .lowCardinalityKeyValue("severity", severity == null ? "unknown" : severity);
        observation.start();
        try (Observation.Scope ignored = observation.openScope()) {
            final EventGuidance result = computeGuidance(alertType, severity);
            observation.lowCardinalityKeyValue("source", result.source().name());
            return result;
        } catch (final RuntimeException e) {
            observation.error(e);
            throw e;
        } finally {
            observation.stop();
        }
    }

    private EventGuidance computeGuidance(final String alertType, final String severity) {
        if (!knowledgeBaseConfigured()) {
            LOGGER.debug("Knowledge Base not configured; using fallback guidance for alertType={}", alertType);
            return fallback(alertType);
        }
        try {
            final BedrockRagQueryResponse rag = ragAdapter.retrieveAndGenerate(
                    new BedrockRagQueryRequest(buildQuestion(alertType, severity), "ALERT_TYPE", null));
            final String grounded = rag == null ? null : rag.answer();
            if (grounded == null || grounded.isBlank() || isSentinel(grounded)) {
                return fallback(alertType);
            }
            final String citation = (rag.citations() == null || rag.citations().isEmpty())
                    ? null : rag.citations().get(0);

            final String formatted = formatPass(alertType, severity, grounded);
            if (formatted != null && !formatted.isBlank()) {
                return new EventGuidance(formatted.trim(), Source.AI_FORMATTED, citation);
            }
            return new EventGuidance(grounded.trim(), Source.KNOWLEDGE_BASE, citation);
        } catch (final RuntimeException e) {
            LOGGER.warn("Event guidance generation failed for alertType={}; using fallback: {}",
                    alertType, e.getMessage());
            return fallback(alertType);
        }
    }

    private boolean knowledgeBaseConfigured() {
        return bedrockProperties.isEnabled()
                && bedrockProperties.getKnowledgeBaseId() != null
                && !bedrockProperties.getKnowledgeBaseId().isBlank();
    }

    private String buildQuestion(final String alertType, final String severity) {
        return String.format(Locale.ROOT,
                "창고 환경 모니터링 알림에 대한 운영 조치 안내를 알려줘. 알림 유형: %s, 심각도: %s. "
                        + "운영자가 즉시 취할 수 있는 구체적인 조치 단계와 주의사항을 한국어로 정리해줘.",
                alertType == null ? "환경 알림" : alertType,
                severity == null ? "WARNING" : severity);
    }

    private String formatPass(final String alertType, final String severity, final String grounded) {
        try {
            final String userPrompt = String.format(Locale.ROOT,
                    "알림 유형: %s%n심각도: %s%n%n[근거]%n%s%n%n위 근거를 조치안내 형식으로 정리하라.",
                    alertType, severity, grounded);
            final AiGenerationResponse response = aiProviderFacade.generate(
                    new AiGenerationRequest(FORMAT_SYSTEM_PROMPT, userPrompt, "EVENT_GUIDANCE", false));
            return response == null ? null : response.text();
        } catch (final RuntimeException e) {
            LOGGER.debug("Guidance format pass failed; using grounded text as-is: {}", e.getMessage());
            return null;
        }
    }

    private boolean isSentinel(final String text) {
        return text.contains("Knowledge Base is not configured")
                || text.contains("Knowledge Base 조회 중 오류");
    }

    private EventGuidance fallback(final String alertType) {
        return new EventGuidance(fallbackText(alertType), Source.FALLBACK, null);
    }

    /** Static Korean guidance per alert-type keyword, used when AI/KB is unavailable. */
    private String fallbackText(final String alertType) {
        final String t = alertType == null ? "" : alertType.toUpperCase(Locale.ROOT);
        if (t.contains("TEMP")) {
            return "1) 냉동/냉장 설비의 가동 및 제상 주기를 즉시 확인하세요.\n"
                    + "2) 도어 밀폐 상태와 장시간 개방 여부를 점검하세요.\n"
                    + "3) 30분 내 회복되지 않으면 담당자에게 보고하고 대체 보관을 준비하세요.";
        }
        if (t.contains("HUMIDITY")) {
            return "1) 제습/가습 설비 동작과 필터 상태를 확인하세요.\n"
                    + "2) 누수·결로 및 출입문 개방 여부를 점검하세요.\n"
                    + "3) 지속 시 담당자에게 보고하고 민감 품목을 우선 이동하세요.";
        }
        if (t.contains("AIR")) {
            return "1) 환기 설비 동작과 급/배기 경로를 확인하세요.\n"
                    + "2) 오염원(차량 배기·작업 분진 등) 유무를 점검하세요.\n"
                    + "3) 기준 초과 지속 시 작업을 중단하고 담당자에게 보고하세요.";
        }
        if (t.contains("DOOR")) {
            return "1) 해당 출입문의 개방 상태와 사유를 즉시 확인하세요.\n"
                    + "2) 무단 개방·끼임 여부를 점검하고 안전하게 폐쇄하세요.\n"
                    + "3) 반복 발생 시 도어 센서·잠금장치 점검을 요청하세요.";
        }
        return "1) 알림 대상 설비/구역의 현재 상태를 즉시 확인하세요.\n"
                + "2) 이상 원인을 점검하고 안전 조치를 취하세요.\n"
                + "3) 회복되지 않으면 담당자에게 보고하세요.";
    }
}
