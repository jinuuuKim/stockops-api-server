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
