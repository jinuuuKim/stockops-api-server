package com.stockops.ai.bedrock.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

/**
 * Single source of truth for the Converse {@code toolConfig} exposed to the model.
 *
 * <p>Each tool here MUST have a matching case in {@link AgentToolDispatcher}; the name set is
 * asserted to match in tests. Descriptions carry a "use when…" hint so the model selects the
 * right tool. All tools are read-only except {@code generateRecommendationSnapshot} and
 * {@code createAISuggestionDraft}, which only write proposal/suggestion rows for human approval.
 *
 * @author StockOps Team
 * @since 2.4
 */
@Component
public class AgentToolCatalog {

    private final ToolConfiguration toolConfiguration;
    private final List<String> toolNames;

    public AgentToolCatalog() {
        final List<Tool> tools = buildTools();
        this.toolConfiguration = ToolConfiguration.builder().tools(tools).build();
        this.toolNames = tools.stream().map(t -> t.toolSpec().name()).toList();
    }

    /**
     * Returns the Converse tool configuration listing every StockOps agent tool.
     *
     * @return tool configuration
     */
    public ToolConfiguration toolConfiguration() {
        return toolConfiguration;
    }

    /**
     * Returns the catalogued tool names (for consistency checks/tests).
     *
     * @return tool names
     */
    public List<String> toolNames() {
        return toolNames;
    }

    private List<Tool> buildTools() {
        final List<Tool> tools = new ArrayList<>();

        tools.add(tool("searchInventory",
                "상품명·바코드·로트(LOT)번호로 재고를 검색한다. 사용자가 숫자 ID가 아닌 이름/바코드/로트번호로 물으면 "
                        + "먼저 이 도구로 상품(productId)·로트와 현재 재고 요약을 찾는다. 로트번호는 'LOT' 또는 'LOT-' 접두사를 붙여 검색해도 된다.",
                props(prop("query", "string", "상품명·바코드·로트번호 검색어 (필수)")),
                List.of("query")));

        tools.add(tool("getInventoryRisk",
                "재고 위험 조회. 특정 상품(productId) 또는 전체 재고의 현재 수량/상태를 본다.",
                props(prop("productId", "integer", "상품 ID (생략 시 전체 재고)")),
                List.of()));

        tools.add(tool("getForecastRecommendation",
                "특정 영업일·센터·창고 범위의 AI 발주 추천 목록을 조회한다.",
                props(prop("businessDate", "string", "영업일 YYYY-MM-DD (생략 시 오늘)"),
                        prop("centerId", "integer", "센터 ID (선택)"),
                        prop("warehouseId", "integer", "창고 ID (선택)")),
                List.of()));

        tools.add(tool("getSensorAnomalies",
                "최근 N일간의 환경 센서 알림(경고/위험)을 조회한다.",
                props(prop("days", "integer", "조회 일수 (기본 7)")),
                List.of()));

        tools.add(tool("getPurchaseOrderDelaySummary",
                "ETA를 지난 미입고 발주 배송 요약(지연 일수 포함)을 조회한다.",
                props(),
                List.of()));

        tools.add(tool("getProphetForecast",
                "특정 상품의 Prophet 수요 예측을 실행한다. 일별 예측값과 상/하한 구간을 반환.",
                props(prop("productId", "integer", "상품 ID (필수)"),
                        prop("days", "integer", "예측 일수 1-30 (기본 7)")),
                List.of("productId")));

        tools.add(tool("getRecentSensorReadings",
                "선택한 센서의 최근 측정값 윈도우(Redis 캐시)를 조회한다.",
                props(prop("sensorId", "integer", "센서 장치 ID (필수)"),
                        prop("minutes", "integer", "조회 분 (보존 윈도우로 캡, 기본 10)")),
                List.of("sensorId")));

        tools.add(tool("getExpiringLots",
                "유통기한 임박 로트 목록을 조회한다.",
                props(prop("days", "integer", "남은 일수 임계값 (기본 30)")),
                List.of()));

        tools.add(tool("getInventoryByLocation",
                "특정 위치(창고/센터 location)의 재고를 조회한다.",
                props(prop("locationId", "integer", "위치 ID (필수)")),
                List.of("locationId")));

        tools.add(tool("getRecommendationExplanationContext",
                "특정 추천(recommendationId)의 설명 근거가 되는 결정적 사실을 조회한다.",
                props(prop("recommendationId", "integer", "추천 ID (필수)")),
                List.of("recommendationId")));

        tools.add(tool("getCenterInventorySummary",
                "특정 센터의 집계 재고 요약을 조회한다.",
                props(prop("centerId", "integer", "센터 ID (필수)")),
                List.of("centerId")));

        tools.add(tool("getInventoryTransactionHistory",
                "재고 입출고 이력을 조회한다. productId가 있으면 해당 상품, 없으면 최근 거래.",
                props(prop("productId", "integer", "상품 ID (선택)"),
                        prop("locationId", "integer", "위치 ID (선택)"),
                        prop("lotId", "integer", "로트 ID (선택)"),
                        prop("limit", "integer", "최근 거래 건수 (productId 없을 때, 기본 20)")),
                List.of()));

        tools.add(tool("getAbcXyzClassification",
                "상품 ABC/XYZ 분류 매트릭스를 조회한다(우선순위 분석).",
                props(prop("centerId", "integer", "센터 ID (선택)")),
                List.of()));

        tools.add(tool("getInventoryTurnover",
                "기간별 재고 회전율/저회전 품목 리포트를 조회한다.",
                props(prop("from", "string", "시작일 YYYY-MM-DD (기본 to-30일)"),
                        prop("to", "string", "종료일 YYYY-MM-DD (기본 오늘)"),
                        prop("centerId", "integer", "센터 ID (선택)")),
                List.of()));

        tools.add(tool("getFillRate",
                "기간별 발주 충족률(fill rate) 리포트를 조회한다.",
                props(prop("from", "string", "시작일 YYYY-MM-DD"),
                        prop("to", "string", "종료일 YYYY-MM-DD"),
                        prop("centerId", "integer", "센터 ID (선택)"),
                        prop("warehouseId", "integer", "창고 ID (선택)")),
                List.of()));

        tools.add(tool("getExpiryWaste",
                "기간별 유통기한 폐기량 리포트를 조회한다.",
                props(prop("from", "string", "시작일 YYYY-MM-DD"),
                        prop("to", "string", "종료일 YYYY-MM-DD"),
                        prop("centerId", "integer", "센터 ID (선택)"),
                        prop("warehouseId", "integer", "창고 ID (선택)")),
                List.of()));

        tools.add(tool("generateRecommendationSnapshot",
                "[쓰기] 영업일 기준 AI 추천을 생성한다. 결과는 승인 대기 제안으로만 저장되며 사람이 웹에서 승인해야 실제 반영된다.",
                props(prop("businessDate", "string", "영업일 YYYY-MM-DD (기본 오늘)"),
                        prop("model", "string", "예측 모델 (statistical/prophet, 선택)"),
                        prop("centerId", "integer", "센터 ID (선택)"),
                        prop("warehouseId", "integer", "창고 ID (선택)")),
                List.of()));

        tools.add(tool("createAISuggestionDraft",
                "[쓰기] 운영 제안 초안을 PENDING 상태로 생성한다. 사람이 승인해야 실제 작업이 진행된다.",
                props(prop("type", "string", "제안 유형"),
                        prop("severity", "string", "심각도"),
                        prop("title", "string", "제목"),
                        prop("summary", "string", "요약"),
                        prop("reason", "string", "근거"),
                        prop("recommendedAction", "string", "권고 조치"),
                        prop("targetScopeType", "string", "대상 범위 유형"),
                        prop("targetScopeId", "integer", "대상 범위 ID")),
                List.of()));

        return tools;
    }

    private static Tool tool(final String name, final String description,
                             final Map<String, Document> properties, final List<String> required) {
        final Map<String, Document> schema = new LinkedHashMap<>();
        schema.put("type", Document.fromString("object"));
        schema.put("properties", Document.fromMap(properties));
        schema.put("required", Document.fromList(required.stream().map(Document::fromString).toList()));
        return Tool.builder()
                .toolSpec(ToolSpecification.builder()
                        .name(name)
                        .description(description)
                        .inputSchema(ToolInputSchema.builder().json(Document.fromMap(schema)).build())
                        .build())
                .build();
    }

    @SafeVarargs
    private static Map<String, Document> props(final Map.Entry<String, Document>... entries) {
        final Map<String, Document> map = new LinkedHashMap<>();
        for (final Map.Entry<String, Document> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    private static Map.Entry<String, Document> prop(final String name, final String type, final String description) {
        final Map<String, Document> field = new LinkedHashMap<>();
        field.put("type", Document.fromString(type));
        field.put("description", Document.fromString(description));
        return Map.entry(name, Document.fromMap(field));
    }
}
