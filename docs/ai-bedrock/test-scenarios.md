# StockOps AI Test Scenarios

## Scenario Template

- ID:
- Feature:
- Test type: API | Unit | Integration | Contract | Build | Browser hands-on | Manual
- Preconditions:
- Steps:
- Expected result:
- Actual result:
- Evidence:
- Status: PASS | FAIL | BLOCKED_BROWSER_UNAVAILABLE | BLOCKED_ENVIRONMENT | NOT_RUN
- If blocked:
  - Block reason:
  - Tests completed instead:
  - Handoff target:
  - Handoff instructions:
  - Required URLs/accounts/test data:
  - Expected screenshots or observations:
  - Related commit/branch:

---

## Browser Hands-On Handoff Queue

Use this section when local Chrome/Chromium execution is unavailable.

### Handoff Item Template

- Scenario ID:
- Why local execution is blocked:
- What has already been tested:
- Exact environment to open:
- Login/account prerequisites:
- Steps for another platform AI tester:
- Expected UI result:
- Expected API/network result:
- Screenshots or logs to capture:
- Pass/fail decision rule:

---

## Phase 0 Scenarios

### TS-P0-001

- ID: TS-P0-001
- Feature: Prophet Model Cache TTL
- Test type: Unit
- Preconditions: ProphetModelCache 클래스 존재
- Steps: TTL=0으로 캐시 생성 → set(1, model) → sleep 0.01s → get(1)
- Expected result: None 반환 (만료)
- Actual result: PASS (pytest Docker exec)
- Status: PASS

### TS-P0-002

- ID: TS-P0-002
- Feature: Prophet Model Cache LRU eviction
- Test type: Unit
- Preconditions: ProphetModelCache 클래스 존재
- Steps: max_size=2로 캐시 생성 → set(1), set(2), get(1), set(3) → get(2)
- Expected result: get(2)는 None (LRU 퇴출), get(1)과 get(3)은 유효
- Actual result: PASS (pytest Docker exec)
- Status: PASS

### TS-P0-003

- ID: TS-P0-003
- Feature: forecasting._fill_missing_dates
- Test type: Unit
- Preconditions: services/forecasting.py 존재
- Steps: [2026-06-01, 2026-06-03] 날짜 데이터로 fill_missing_dates 호출
- Expected result: [5.0, 0.0, 7.0]
- Actual result: PASS (pytest Docker exec)
- Status: PASS

### TS-P0-004

- ID: TS-P0-004
- Feature: forecast_async 결과 구조
- Test type: Unit
- Preconditions: FakeModel monkeypatching 가능
- Steps: fake_train monkeypatch → forecast_async(product_id=1, days=2)
- Expected result: product_id=1, days=2, forecast[0].yhat=10.25
- Actual result: PASS (pytest Docker exec)
- Status: PASS

### TS-P0-005

- ID: TS-P0-005
- Feature: /predict/bulk 부분 실패
- Test type: API
- Preconditions: TestClient 가용, product_id=2가 ValueError 발생하도록 monkeypatch
- Steps: POST /predict/bulk {"products": [{"product_id":1,"days":1},{"product_id":2,"days":1}]}
- Expected result: HTTP 400, detail.successful=1, "product_id=2" in errors
- Actual result: PASS (pytest Docker exec)
- Status: PASS

---

## Phase 1 Scenarios

### TS-P1-001

- ID: TS-P1-001
- Feature: BedrockAiProperties.generationModelReference() 우선순위
- Test type: Unit
- Preconditions: BedrockAiProperties 클래스 존재
- Steps: inferenceProfileArn 설정 → generationModelReference() 호출
- Expected result: inferenceProfileArn 반환
- Actual result: PASS (mvn test)
- Status: PASS

### TS-P1-002

- ID: TS-P1-002
- Feature: AiForecastClient X-API-Key 헤더 주입
- Test type: Unit (MockRestServiceServer)
- Preconditions: AiForecastProperties에 apiKey 설정
- Steps: apiKey "test-key" 설정 → getForecast 호출 → 요청 헤더 검증
- Expected result: X-API-Key: test-key 헤더 포함
- Actual result: PASS (mvn test)
- Status: PASS

### TS-P1-003

- ID: TS-P1-003
- Feature: BedrockPromptBuilder 프롬프트 팩트 포함 여부
- Test type: Unit
- Preconditions: BedrockPromptBuilder 클래스 존재
- Steps: AIRecommendationDTO 생성 → buildRecommendationExplanationPrompt 호출
- Expected result: productName, recommendedQuantity 등 팩트 포함
- Actual result: PASS (mvn test)
- Status: PASS

### TS-P1-004

- ID: TS-P1-004
- Feature: BedrockPromptBuilder 이중인용부호 sanitize
- Test type: Unit
- Preconditions: BedrockPromptBuilder 클래스 존재
- Steps: productName에 `"특수"` 포함 DTO → buildRecommendationExplanationPrompt 호출
- Expected result: 프롬프트에 `\"특수\"` (escape) 포함, 날것 `"특수"` 없음
- Actual result: PASS (mvn test)
- Status: PASS

### TS-P1-005

- ID: TS-P1-005
- Feature: AiProviderFacade UNCONFIGURED 응답
- Test type: Unit
- Preconditions: 두 공급자 모두 disabled
- Steps: generate(chatVisible=true) 호출
- Expected result: serviceStatus=UNCONFIGURED, serviceNotice=noServiceNotice
- Actual result: PASS (mvn test)
- Status: PASS

### TS-P1-006

- ID: TS-P1-006
- Feature: AiProviderFacade UNAUTHENTICATED 폴백
- Test type: Unit
- Preconditions: Bedrock enabled → AccessDenied 예외, Vertex disabled
- Steps: generate(chatVisible=true) 호출
- Expected result: serviceStatus=UNAUTHENTICATED, serviceNotice=unauthenticatedNotice
- Actual result: PASS (mvn test)
- Status: PASS

### TS-P1-007

- ID: TS-P1-007
- Feature: BedrockAiFacade Bedrock 비활성화 시 폴백 응답
- Test type: Unit
- Preconditions: properties.isEnabled()=false
- Steps: explainRecommendation(dto) 호출
- Expected result: modelId="fallback", summary에 추천 수량 포함
- Actual result: PASS (mvn test)
- Status: PASS

### TS-P1-008

- ID: TS-P1-008
- Feature: AiChatController POST /api/v1/ai/chat/messages 응답 매핑
- Test type: Unit (MockMvc standalone)
- Preconditions: AiProviderFacade mock
- Steps: POST /api/v1/ai/chat/messages {"message":"재고 현황 알려줘"} 호출
- Expected result: HTTP 200, message/provider/fallbackNotice 필드 매핑 정확
- Actual result: PASS (mvn test)
- Status: PASS

### TS-P1-009

- ID: TS-P1-009
- Feature: AiChatController chatVisible=true 전달
- Test type: Unit (MockMvc standalone)
- Preconditions: AiProviderFacade mock
- Steps: POST /api/v1/ai/chat/messages → AiGenerationRequest.chatVisible 검증
- Expected result: chatVisible=true, useCase="CHAT"
- Actual result: PASS (mvn test)
- Status: PASS

### TS-P1-010

- ID: TS-P1-010
- Feature: GET /api/v1/ai/bedrock/ops-summary Bedrock 비활성화 폴백
- Test type: Browser hands-on (로컬 Bedrock 미구성)
- Preconditions: stockops-api-server 실행, 인증 토큰 보유
- Steps: GET /api/v1/ai/bedrock/ops-summary?businessDate=2025-06-09 호출
- Expected result: HTTP 200, summary="AI 운영 요약 서비스가 비활성화 상태입니다."
- Actual result: NOT_RUN
- Status: BLOCKED_ENVIRONMENT
- If blocked:
  - Block reason: 로컬 Bedrock 자격증명 없음, stockops.ai.bedrock.enabled=false
  - Tests completed instead: BedrockAiFacadeTest.summarizeOperations_returnsPlaceholderWhenBedrockDisabled (Unit, PASS)
  - Handoff target: 배포 환경 또는 Bedrock 자격증명 보유 개발자
  - Handoff instructions: stockops.ai.bedrock.enabled=true 설정 후 API 호출

### TS-P1-011

- ID: TS-P1-011
- Feature: POST /api/v1/ai/bedrock/recommendations/{id}/explain Bedrock 비활성화 폴백
- Test type: Browser hands-on (로컬 Bedrock 미구성)
- Preconditions: stockops-api-server 실행, AIRecommendation 레코드 존재, 인증 토큰 보유
- Steps: POST /api/v1/ai/bedrock/recommendations/1/explain
- Expected result: HTTP 200, modelId="fallback"
- Actual result: NOT_RUN
- Status: BLOCKED_ENVIRONMENT
- If blocked:
  - Block reason: 로컬 Bedrock 자격증명 없음
  - Tests completed instead: BedrockAiFacadeTest.explainRecommendation_returnsFallbackWhenBedrockDisabled (Unit, PASS)

---

## Phase 1 - Task 8 Scenarios

### TS-P1-012

- ID: TS-P1-012
- Feature: BedrockAiFacade Agent→AISuggestion 연동 (actionSuggested=true, scope 제공)
- Test type: Unit
- Preconditions: agentAdapter mock, aiSuggestionService mock
- Steps: invokeAgent(message, scope=WAREHOUSE/2) → agentAdapter returns actionSuggested=true
- Expected result: aiSuggestionService.create called with source=BEDROCK_AGENT, sourceType=AI_AGENT, approvalMode=MANUAL_APPROVAL_REQUIRED
- Actual result: PASS (mvn test)
- Status: PASS

### TS-P1-013

- ID: TS-P1-013
- Feature: BedrockAiFacade Agent 제안 없을 때 AISuggestion 미생성
- Test type: Unit
- Preconditions: agentAdapter mock
- Steps: invokeAgent → agentAdapter returns actionSuggested=false
- Expected result: aiSuggestionService.create 호출 없음
- Actual result: PASS (mvn test)
- Status: PASS

### TS-P1-014

- ID: TS-P1-014
- Feature: BedrockAiFacade scope 미제공 시 AISuggestion 미생성
- Test type: Unit
- Preconditions: agentAdapter returns actionSuggested=true, scope null
- Steps: invokeAgent(message, scope=null/null) → actionSuggested=true
- Expected result: aiSuggestionService.create 호출 없음 (scope 없이 AISuggestion 생성 불가)
- Actual result: PASS (mvn test)
- Status: PASS

### TS-P1-015

- ID: TS-P1-015
- Feature: BedrockLiveSmokeTest CI 기본 실행 제외
- Test type: Build
- Preconditions: STOCKOPS_BEDROCK_LIVE_TESTS 환경변수 미설정
- Steps: mvn test 실행
- Expected result: BedrockLiveSmokeTest 실행되지 않음
- Actual result: NOT_RUN
- Status: BLOCKED_ENVIRONMENT
- If blocked:
  - Block reason: 환경변수 미설정으로 @EnabledIfEnvironmentVariable에 의해 자동 skip
  - Tests completed instead: 기본 mvn test에 BedrockLiveSmokeTest가 포함되지 않음을 확인 예정

---

## Phase 2 Test Scenarios

### TS-P2-001: AiCallMetrics — 성공 호출 지표 등록

- ID: TS-P2-001
- Feature: AI 호출 감사 로깅 및 Micrometer 지표 (Task 1)
- Test type: Unit
- Preconditions: SimpleMeterRegistry 사용
- Steps: AiCallMetrics.record(success=true)
- Expected result: ai.bedrock.requests counter +1, ai.bedrock.latency timer 기록
- Status: PASS
- Evidence: AiCallMetricsTest.record_successfulBedrockCall_incrementsCounterAndRecordsTimer

### TS-P2-002: AiCallMetrics — fallback 호출 태그 확인

- ID: TS-P2-002
- Feature: AI 호출 감사 로깅 및 Micrometer 지표 (Task 1)
- Test type: Unit
- Steps: AiCallMetrics.record(fallbackUsed=true)
- Expected result: fallback=true 태그 포함 counter 증가
- Status: PASS
- Evidence: AiCallMetricsTest.record_fallbackVertexCall_tagsFallbackTrue

### TS-P2-003: AiRagRateLimiter — 한도 초과 시 RateLimitExceededException

- ID: TS-P2-003
- Feature: RAG 사용자별 Rate Limiting (Task 4)
- Test type: Unit
- Steps: 동일 userKey로 limit+1회 호출
- Expected result: limit+1번째 호출에서 RateLimitExceededException
- Status: PASS
- Evidence: AiRagRateLimiterTest.checkRagLimit_exceededLimit_throwsRateLimitExceededException

### TS-P2-004: AiRagRateLimiter — 사용자 독립 버킷

- ID: TS-P2-004
- Feature: RAG 사용자별 Rate Limiting (Task 4)
- Test type: Unit
- Steps: user1 한도 소진 후 user2 호출
- Expected result: user2는 정상 처리
- Status: PASS
- Evidence: AiRagRateLimiterTest.checkRagLimit_differentUsers_haveIndependentBuckets

### TS-P2-005: AiOpsSummaryScheduler — 배치 정상 실행

- ID: TS-P2-005
- Feature: 운영 요약 배치 스케줄링 (Task 3)
- Test type: Unit
- Steps: scheduler.generateDailyOpsSummaries() 호출
- Expected result: bedrockAiFacade.summarizeOperations(today, null, null) 1회 호출
- Status: PASS
- Evidence: AiOpsSummarySchedulerTest.generateDailyOpsSummaries_callsSummarizeOperationsForToday

### TS-P2-006: AiOpsSummaryScheduler — 예외 시 스레드 유지

- ID: TS-P2-006
- Feature: 운영 요약 배치 스케줄링 (Task 3)
- Test type: Unit
- Steps: summarizeOperations 예외 발생 시 generateDailyOpsSummaries 호출
- Expected result: 예외 미전파 (assertDoesNotThrow)
- Status: PASS
- Evidence: AiOpsSummarySchedulerTest.generateDailyOpsSummaries_whenSummarizeFails_doesNotThrow

### TS-P2-007: AgentToolDispatcher — getInventoryRisk (productId 없음)

- ID: TS-P2-007
- Feature: Agent Tool Dispatcher (Task 6)
- Test type: Unit
- Steps: dispatch("getInventoryRisk", "{}")
- Expected result: success=true, getAllInventory() 호출
- Status: PASS
- Evidence: AgentToolDispatcherTest.dispatch_getInventoryRisk_withoutProductId_callsGetAllInventory

### TS-P2-008: AgentToolDispatcher — 알 수 없는 tool

- ID: TS-P2-008
- Feature: Agent Tool Dispatcher (Task 6)
- Test type: Unit
- Steps: dispatch("nonExistentTool", "{}")
- Expected result: success=false, errorMessage 포함
- Status: PASS
- Evidence: AgentToolDispatcherTest.dispatch_unknownTool_returnsFailure

### TS-P2-009: AiExplanationPanel — 설명 요청 및 렌더링 (Frontend)

- ID: TS-P2-009
- Feature: 추천 설명 패널 (Task 7)
- Test type: Unit (vitest)
- Steps: "AI 설명 보기" 버튼 클릭
- Expected result: dialog 렌더링, summary 텍스트 표시
- Status: PASS
- Evidence: AiExplanationPanel.test.tsx — shows explanation summary after successful fetch

### TS-P2-010: AiExplanationPanel — 재클릭 시 API 재호출 없음 (Frontend)

- ID: TS-P2-010
- Feature: 추천 설명 패널 (Task 7)
- Test type: Unit (vitest)
- Steps: 버튼 2회 클릭 (닫기 후 재열기)
- Expected result: fetchRecommendationExplanation 1회만 호출
- Status: PASS
- Evidence: AiExplanationPanel.test.tsx — does not re-fetch if explanation is already loaded

---

## TS-P2-011: 토큰 카운터 — Bedrock 성공 호출

- ID: TS-P2-011
- 대상: AiCallMetrics.record()
- 전제: AiCallRecord.inputTokens=120, outputTokens=85
- 실행: aiCallMetrics.record(record)
- 기대: ai.bedrock.tokens{direction=input} == 120, ai.bedrock.tokens{direction=output} == 85
- 자동화: AiCallMetricsTest.record_successfulBedrockCall_incrementsCounterAndRecordsTimer (unit)

## TS-P2-012: 토큰 카운터 — null 토큰 케이스

- ID: TS-P2-012
- 대상: AiCallMetrics.record()
- 전제: AiCallRecord.inputTokens=null, outputTokens=null (Vertex 또는 실패 케이스)
- 실행: aiCallMetrics.record(record)
- 기대: ai.bedrock.tokens 카운터 미등록
- 자동화: AiCallMetricsTest.record_nullTokenCounts_doesNotRegisterTokenCounter (unit)

---

## TS-P2-013: getPurchaseOrderDelaySummary — 빈 목록

- ID: TS-P2-013
- 대상: AgentToolDispatcher.dispatch("getPurchaseOrderDelaySummary", ...)
- 전제: 연체 출하 없음
- 실행: dispatcher.dispatch("getPurchaseOrderDelaySummary", "{}")
- 기대: success=true, resultJson="[]"
- 자동화: AgentToolDispatcherTest.dispatch_getPurchaseOrderDelaySummary_emptyList_returnsEmptyJson (unit)

## TS-P2-014: getPurchaseOrderDelaySummary — 연체 출하 포함

- ID: TS-P2-014
- 대상: AgentToolDispatcher.dispatch("getPurchaseOrderDelaySummary", ...)
- 전제: etaDate=오늘-3일인 출하 1건
- 실행: dispatcher.dispatch("getPurchaseOrderDelaySummary", "{}")
- 기대: success=true, resultJson에 daysOverdue=3, SHP-001 포함
- 자동화: AgentToolDispatcherTest.dispatch_getPurchaseOrderDelaySummary_withOverdueShipment_includesDaysOverdue (unit)

---

## TS-P2-015: Bedrock JSON 파싱 — 정상 JSON 응답

- ID: TS-P2-015
- 대상: BedrockAiFacade.explainRecommendation()
- 전제: Bedrock이 {"summary":"...","reasons":[...],"reviewerChecklist":[...],"riskLevel":"HIGH"} 형식으로 응답
- 실행: explainRecommendation(dto)
- 기대: response.summary() == JSON summary 필드값, reasons/reviewerChecklist == JSON 배열값, riskLevel == "HIGH"
- 자동화: BedrockAiFacadeTest.explainRecommendation_parsesJsonFieldsFromBedrockResponse (unit)

## TS-P2-016: Bedrock JSON 파싱 — 마크다운 코드펜스

- ID: TS-P2-016
- 대상: BedrockAiFacade.explainRecommendation()
- 전제: Bedrock 응답이 ```json ... ``` 코드펜스로 감싸져 있음
- 실행: explainRecommendation(dto)
- 기대: 코드펜스 제거 후 JSON 파싱 성공, summary/reasons/riskLevel 정상 반환
- 자동화: BedrockAiFacadeTest.explainRecommendation_stripsMarkdownCodeFenceFromBedrockResponse (unit)

## TS-P2-017: Bedrock JSON 파싱 — 비JSON 응답 안전 fallback (§8 정책)

- ID: TS-P2-017
- 대상: BedrockAiFacade.explainRecommendation()
- 전제: Bedrock 응답이 JSON이 아닌 평문 텍스트
- 실행: explainRecommendation(dto)
- 기대: §8 정책 준수 — raw model text가 summary에 반환되지 않음, fallbackExplanation() 결과 반환
  - response.summary() 포함: "AI 설명을 생성하지 못했습니다"
  - response.modelId() == "fallback"
  - response.reasons() == ["응답 JSON 파싱 실패"]
- 자동화: BedrockAiFacadeTest.explainRecommendation_returnsSafeFallbackWhenJsonInvalid (unit)
- 변경 이력: TS-P2-017 Phase 3에서 수정 — 기존 "raw text 반환" 어설션이 §8 정책 위반임을 확인하고 수정

---

## Phase 3 테스트 시나리오

## TS-P3-001: Vertex AI — usageMetadata 정상 추출

- ID: TS-P3-001
- 대상: VertexAiGenerationProvider.generate()
- 전제: mock GenerateContentResponse — text()="AI 응답", usageMetadata()=Optional.of(meta), meta.promptTokenCount()=120, meta.candidatesTokenCount()=80
- 실행: provider.generate(request)
- 기대: result.inputTokens() == 120, result.outputTokens() == 80, result.fallbackUsed() == true
- 자동화: VertexAiGenerationProviderTest.generate_extractsTokenCountsFromUsageMetadata (unit)

## TS-P3-002: Vertex AI — usageMetadata absent 처리

- ID: TS-P3-002
- 대상: VertexAiGenerationProvider.generate()
- 전제: mock response — usageMetadata()=Optional.empty()
- 실행: provider.generate(request)
- 기대: result.inputTokens() == null, result.outputTokens() == null, 예외 없음
- 자동화: VertexAiGenerationProviderTest.generate_handlesAbsentUsageMetadataGracefully (unit)

## TS-P3-003: Vertex AI — provider disabled 예외

- ID: TS-P3-003
- 대상: VertexAiGenerationProvider.generate()
- 전제: properties.isEnabled() == false
- 실행: provider.generate(request)
- 기대: IllegalStateException("disabled")
- 자동화: VertexAiGenerationProviderTest.generate_throwsWhenProviderDisabled (unit)

## TS-P3-004: Vertex AI — projectId 미설정 예외

- ID: TS-P3-004
- 대상: VertexAiGenerationProvider.generate()
- 전제: isEnabled=true, projectId=null
- 실행: provider.generate(request)
- 기대: IllegalStateException("project id")
- 자동화: VertexAiGenerationProviderTest.generate_throwsWhenProjectIdNotConfigured (unit)

## TS-P3-005: Vertex AI — 빈 응답 텍스트 예외

- ID: TS-P3-005
- 대상: VertexAiGenerationProvider.generate()
- 전제: mock response — text()=""
- 실행: provider.generate(request)
- 기대: IllegalStateException("empty")
- 자동화: VertexAiGenerationProviderTest.generate_throwsWhenResponseTextIsBlank (unit)

## TS-P3-006: Vertex AI — chatVisible=true → fallbackNotice 포함

- ID: TS-P3-006
- 대상: VertexAiGenerationProvider.generate()
- 전제: request.chatVisible() == true, properties.getFallbackNotice() == "기본 제공 모델의 연결이 불안정합니다."
- 실행: provider.generate(chatRequest)
- 기대: result.fallbackNotice() == "기본 제공 모델의 연결이 불안정합니다."
- 자동화: VertexAiGenerationProviderTest.generate_includesFallbackNoticeOnlyForChatVisibleRequests (unit)

---

## TS-P3-007: 운영 요약 sourceCounts — 결정론적 소스 건수 포함

- ID: TS-P3-007
- 대상: BedrockAiFacade.summarizeOperations()
- 전제: 추천 0건, 센서 알림 0건, 만료 경보 0건 (all stubs return empty)
- 실행: summarizeOperations(date, centerId, warehouseId)
- 기대:
  - response.sourceCounts().get("recommendations") == 0
  - response.sourceCounts().get("sensorAlerts") == 0
  - response.confidenceCaveat() contains "데이터가 부족"
- 자동화: BedrockAiFacadeTest.summarizeOperations_parsesJsonFieldsFromBedrockResponse (unit, assertions 추가)

## TS-P3-008: 운영 요약 confidenceCaveat — 충분한 데이터 → 출처 요약 메시지

- ID: TS-P3-008
- 대상: BedrockAiFacade.OpsFacts.buildConfidenceCaveat()
- 전제: recommendationCount=5, sensorAlertCount=3, criticalExpiry=1, warningExpiry=2 (total=11 ≥ 5)
- 기대: confidenceCaveat contains "추천 5건, 센서 알림 3건, 만료 경보 3건"
- 자동화: 단위 레코드 테스트 없음 (integration-tested via BedrockAiFacadeTest)

## TS-P4-001: Ops facts — overdueShipmentCount 포함 확인

- ID: TS-P4-001
- Feature: Phase 4 - ops facts enrichment (overdueShipmentCount)
- Test type: Unit
- 대상: BedrockAiFacade.buildOpsFacts() → OpsFacts.toSourceCounts()
- 전제: shipmentRepository.findByEtaDateBeforeAndDeliveredAtIsNull(businessDate) 반환 빈 리스트, 나머지 데이터도 빈 값
- 실행: summarizeOperations(date, centerId, warehouseId)
- 기대: response.sourceCounts()["overdueShipments"] == 0, "데이터가 부족"이 confidenceCaveat에 포함
- 자동화: BedrockAiFacadeTest.summarizeOperations_parsesJsonFieldsFromBedrockResponse (unit)
- 실제: PASS
- 상태: PASS

## TS-P4-002: Ops facts — shipmentRepository 오류 시 graceful degradation

- ID: TS-P4-002
- Feature: Phase 4 - ops facts enrichment (fault tolerance)
- Test type: Unit (수동 검증)
- 대상: BedrockAiFacade.buildOpsFacts() — shipmentRepository 예외 처리
- 전제: shipmentRepository.findByEtaDateBeforeAndDeliveredAtIsNull() 호출 시 RuntimeException 발생
- 기대: log.warn 출력, overdueShipmentCount=0으로 폴백, 요약 생성 계속 진행
- 자동화: 없음 (코드 검토로 검증)
- 상태: NOT_RUN

---

## TS-P4-003: AiOpsSummaryPanel — 초기 버튼 표시

- ID: TS-P4-003
- Feature: Phase 4 - AiOpsSummaryPanel (frontend)
- Test type: Unit (vitest)
- 대상: AiOpsSummaryPanel 컴포넌트 초기 렌더
- 기대: "AI 운영 요약 보기" 버튼 표시
- 자동화: AiOpsSummaryPanel.test.tsx (vitest)
- 상태: PASS

## TS-P4-004: AiOpsSummaryPanel — fetch 후 요약/긴급 항목/권장 조치 표시

- ID: TS-P4-004
- Feature: Phase 4 - AiOpsSummaryPanel (frontend)
- Test type: Unit (vitest)
- 대상: AiOpsSummaryPanel fetch 후 표시 내용
- 기대: summary, urgentItems, recommendedActions, riskLevel 배지 표시
- 자동화: AiOpsSummaryPanel.test.tsx (vitest)
- 상태: PASS

## TS-P4-005: AiOpsSummaryPanel — sourceCounts 칩 표시 (§5.4)

- ID: TS-P4-005
- Feature: Phase 4 - AiOpsSummaryPanel (frontend)
- Test type: Unit (vitest)
- 대상: AiOpsSummaryPanel.sourceCounts 렌더링
- 기대: "데이터 출처" 섹션에 "추천", "지연 PO" 등 칩 표시
- 자동화: AiOpsSummaryPanel.test.tsx (vitest)
- 상태: PASS

## TS-P4-006: AiOpsSummaryPanel — confidenceCaveat 접기/펼치기

- ID: TS-P4-006
- Feature: Phase 4 - AiOpsSummaryPanel (frontend)
- Test type: Unit (vitest)
- 대상: AiOpsSummaryPanel.confidenceCaveat 접기/펼치기
- 전제: 패널 열린 상태
- 실행: "신뢰도 안내" 버튼 클릭
- 기대: confidenceCaveat 텍스트 표시
- 자동화: AiOpsSummaryPanel.test.tsx (vitest)
- 상태: PASS

---

## TS-CTRL-001: 컨트롤러 — explainRecommendation 200 응답

- ID: TS-CTRL-001
- Feature: BedrockAiController (§9 컨트롤러 테스트)
- Test type: Unit (standalone MockMvc)
- 대상: POST /api/v1/ai/bedrock/recommendations/1/explain
- 기대: recommendationId, summary, riskLevel, reasons, modelId 포함 200 응답
- 자동화: BedrockAiControllerTest.explainRecommendation_returns200WithExplanationFields
- 상태: PASS

## TS-CTRL-002: 컨트롤러 — opsSummary sourceCounts/confidenceCaveat 직렬화

- ID: TS-CTRL-002
- Feature: BedrockAiController (§5.4, §9)
- Test type: Unit (standalone MockMvc)
- 대상: GET /api/v1/ai/bedrock/ops-summary
- 기대: sourceCounts["recommendations"], sourceCounts["overdueShipments"], confidenceCaveat 포함 200 응답
- 자동화: BedrockAiControllerTest.opsSummary_returns200WithSourceCountsAndConfidenceCaveat
- 상태: PASS

## TS-CTRL-003: 컨트롤러 — RAG rate-limiter null-guard

- ID: TS-CTRL-003
- Feature: BedrockAiController (§6.3 rate limiting)
- Test type: Unit (standalone MockMvc)
- 대상: POST /api/v1/ai/bedrock/rag/query — null Authentication 시 rate-limiter 미호출
- 기대: checkRagLimit() 미호출, 200 응답 (standalone MockMvc에는 Authentication 없음)
- 자동화: BedrockAiControllerTest.queryKnowledgeBase_invokesRateLimiterWhenAuthenticationIsNull
- 상태: PASS

## TS-SEC-001: 프롬프트 빌더 백슬래시 sanitization (§7)

- ID: TS-SEC-001
- Feature: BedrockPromptBuilder (§7 보안)
- Test type: Unit
- 대상: BedrockPromptBuilder.sanitize() — 백슬래시 이스케이프
- 전제: productName에 백슬래시 포함
- 기대: 프롬프트에 이스케이프된 백슬래시 포함, 원본 미포함
- 자동화: BedrockPromptBuilderTest.buildRecommendationExplanationPrompt_sanitizesBackslash
- 상태: PASS

---

## Phase 5 테스트 시나리오

### TS-P5-REPO-001: 재고 없는 상품 → 집계

- ID: TS-P5-REPO-001
- Feature: ProductRepository.countProductsBelowSafetyStock()
- Test type: DataJpaTest (H2 in-memory)
- 대상: safetyStock=10, 재고 행 없음 → COALESCE(SUM..., 0) = 0 < 10
- 기대: countProductsBelowSafetyStock() == 1 (가장 중요한 재고 소진 케이스)
- 자동화: ProductRepositoryTest.countProductsBelowSafetyStock_noInventoryRows_isCounted
- 상태: PASS

### TS-P5-REPO-002: available < safety → 집계

- ID: TS-P5-REPO-002
- Feature: ProductRepository.countProductsBelowSafetyStock()
- Test type: DataJpaTest
- 대상: safetyStock=10, qty=5, reserved=0 → available=5 < 10
- 기대: countProductsBelowSafetyStock() == 1
- 자동화: ProductRepositoryTest.countProductsBelowSafetyStock_availableBelowSafety_isCounted
- 상태: PASS

### TS-P5-REPO-003: available ≥ safety → 미집계

- ID: TS-P5-REPO-003
- Feature: ProductRepository.countProductsBelowSafetyStock()
- Test type: DataJpaTest
- 대상: safetyStock=10, qty=15, reserved=0 → available=15 ≥ 10
- 기대: countProductsBelowSafetyStock() == 0
- 자동화: ProductRepositoryTest.countProductsBelowSafetyStock_availableMeetsSafety_isNotCounted
- 상태: PASS

### TS-P5-REPO-004: safetyStockQuantity=0 → 미집계

- ID: TS-P5-REPO-004
- Feature: ProductRepository.countProductsBelowSafetyStock()
- Test type: DataJpaTest
- 대상: WHERE p.safetyStockQuantity > 0 에 의해 제외
- 기대: countProductsBelowSafetyStock() == 0
- 자동화: ProductRepositoryTest.countProductsBelowSafetyStock_safetyStockZero_isExcluded
- 상태: PASS

### TS-P5-REPO-005: 삭제된 상품 → 미집계

- ID: TS-P5-REPO-005
- Feature: ProductRepository.countProductsBelowSafetyStock() + @SQLRestriction
- Test type: DataJpaTest
- 대상: deleted=true 상품은 @SQLRestriction("deleted = false")에 의해 자동 제외
- 기대: countProductsBelowSafetyStock() == 0
- 자동화: ProductRepositoryTest.countProductsBelowSafetyStock_deletedProduct_isExcluded
- 상태: PASS

### TS-P5-REPO-006: reserved 수량 공제 → 집계

- ID: TS-P5-REPO-006
- Feature: ProductRepository.countProductsBelowSafetyStock()
- Test type: DataJpaTest
- 대상: safetyStock=10, qty=12, reserved=5 → available=7 < 10
- 기대: countProductsBelowSafetyStock() == 1
- 자동화: ProductRepositoryTest.countProductsBelowSafetyStock_reservedReducesAvailable_isCounted
- 상태: PASS

### TS-P5-REPO-007: 혼합 시나리오 — 2건만 집계

- ID: TS-P5-REPO-007
- Feature: ProductRepository.countProductsBelowSafetyStock()
- Test type: DataJpaTest
- 대상: 재고 없음(1건) + available 부족(1건) + available 충족(1건) + 삭제됨(1건) + safetyStock=0(1건)
- 기대: countProductsBelowSafetyStock() == 2
- 자동화: ProductRepositoryTest.countProductsBelowSafetyStock_mixedProducts_countsOnlyBelowThreshold
- 상태: PASS

### TS-P5-001: OpsFacts — inventoryBelowSafetyStock 포함 확인

- ID: TS-P5-001
- Feature: Phase 5 - ops facts enrichment (inventoryBelowSafetyStock)
- Test type: Unit
- 대상: BedrockAiFacade.buildOpsFacts() → OpsFacts.toSourceCounts()
- 전제: productRepository.countProductsBelowSafetyStock() 반환 0L, 나머지 데이터도 빈 값
- 실행: summarizeOperations(date, centerId, warehouseId)
- 기대: response.sourceCounts()["inventoryBelowSafetyStock"] == 0
- 자동화: BedrockAiFacadeTest.summarizeOperations_parsesJsonFieldsFromBedrockResponse (unit)
- 상태: PASS

### TS-P5-002: OpsFacts — productRepository 오류 시 graceful degradation

- ID: TS-P5-002
- Feature: Phase 5 - ops facts enrichment (fault tolerance)
- Test type: Unit (코드 검토)
- 대상: BedrockAiFacade.buildOpsFacts() — productRepository 예외 처리
- 전제: productRepository.countProductsBelowSafetyStock() 호출 시 RuntimeException 발생
- 기대: log.warn 출력, inventoryBelowSafetyStockCount=0으로 폴백, 요약 생성 계속 진행
- 자동화: 없음 (코드 검토로 검증)
- 상태: NOT_RUN

---

## TS-P2-018: Bedrock 운영 요약 JSON 파싱

- ID: TS-P2-018
- 대상: BedrockAiFacade.summarizeOperations()
- 전제: Bedrock이 {"summary":"...","urgentItems":[...],"recommendedActions":[...],"riskLevel":"HIGH"} 형식으로 응답
- 실행: summarizeOperations(date, centerId, warehouseId)
- 기대: summary/urgentItems/recommendedActions/riskLevel이 JSON에서 파싱됨
- 자동화: BedrockAiFacadeTest.summarizeOperations_parsesJsonFieldsFromBedrockResponse (unit)

---

## Phase 6 Scenarios

### TS-P6-001: recentPrivilegeEventCount — sourceCounts 반영

- ID: TS-P6-001
- Feature: Phase 6 - ops facts privilege event enrichment
- Test type: Unit
- 대상: BedrockAiFacade.buildOpsFacts() → OpsFacts.toSourceCounts()
- 전제: auditLogRepository.countByEntityTypeInAndPerformedAtAfter(PRIVILEGE_ENTITY_TYPES, since) 5L 반환
- 실행: summarizeOperations(date, null, null)
- 기대: response.sourceCounts()["recentPrivilegeEvents"] == 5, confidenceCaveat contains "권한 변경 5건"
- 자동화: BedrockAiFacadeTest.summarizeOperations_recentPrivilegeEventCountFlowsThroughSourceCounts
- 상태: PASS

### TS-P6-002: auditLogRepository 오류 시 graceful degradation

- ID: TS-P6-002
- Feature: Phase 6 - ops facts fault tolerance
- Test type: Unit
- 대상: BedrockAiFacade.buildOpsFacts() — auditLogRepository 예외 처리
- 전제: auditLogRepository.countByEntityTypeInAndPerformedAtAfter() RuntimeException 발생
- 기대: recentPrivilegeEventCount=0 폴백, 요약 생성 계속 진행, 예외 전파 없음
- 자동화: BedrockAiFacadeTest.summarizeOperations_privilegeEventQueryFails_degradesGracefully
- 상태: PASS
