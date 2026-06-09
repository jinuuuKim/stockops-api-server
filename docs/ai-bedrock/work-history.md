# StockOps AI Work History

## Entry Template

- Date:
- Phase:
- Summary:
- Files changed:
- Decisions:
- Blockers:
- Verification:
- Next step:

---

## 2026-06-09 | Documentation Task

- Date: 2026-06-09
- Phase: Documentation Task
- Summary: 작업추적 아티팩트(work-history.md, test-scenarios.md, phase-reviews.md) 생성. Phase Governance 게이트 충족.
- Files changed:
  - docs/ai-bedrock/work-history.md (신규)
  - docs/ai-bedrock/test-scenarios.md (신규)
  - docs/ai-bedrock/phase-reviews.md (신규)
- Decisions:
  - 문서 경로: stockops-api-server/docs/ai-bedrock/ (설계문서 기준)
  - 문서 구조: 계획서 템플릿 그대로 사용
- Blockers: 없음
- Verification: 파일 생성 확인
- Next step: Phase 0 (stockops-ai-module 안정화)

---

## 2026-06-09 | Phase 0

- Date: 2026-06-09
- Phase: Phase 0 - FastAPI/Prophet Forecast Service Stabilization
- Summary: Prophet 모델 캐시 테스트(TTL/LRU), 예측 서비스 테스트(fill_missing_dates, forecast_async), bulk 부분 실패 테스트 추가. README API 계약 완성, 환경변수 표 보완.
- Files changed (stockops-ai-module):
  - tests/test_prophet_model_cache.py (신규)
  - tests/test_forecasting_service.py (신규)
  - tests/test_api_contract.py (bulk partial failure 테스트 추가)
  - README.md (API 계약, 환경변수 표 완성)
- Decisions:
  - .env.example은 이미 모든 필요 변수 포함 → 수정 불필요
  - Python이 호스트에 없어서 Docker 컨테이너 내에서 pytest 실행
- Blockers: 없음
- Verification: pytest 18/18 PASS (Docker exec 확인)
- Next step: Phase 1 - Spring Forecast Client Contract / Task 1

---

## 2026-06-09 | Phase 1 - Task 1 (Spring Config Foundations)

- Date: 2026-06-09
- Phase: Phase 1 - Spring AI Infrastructure
- Summary: Spring Boot AI 기반 설정 작업 완료. AWS SDK 및 Google GenAI 의존성 추가, 설정 프로퍼티 클래스 생성, application.yml 확장.
- Files changed:
  - pom.xml (AWS SDK 2.44.13 bedrockruntime+agentruntime, google-genai 1.5.0 추가)
  - src/main/resources/application.yml (stockops.ai.bedrock, stockops.ai.vertex 블록 추가)
  - src/main/java/com/stockops/ai/bedrock/BedrockAiProperties.java (신규)
  - src/main/java/com/stockops/ai/gcp/VertexAiProperties.java (신규)
  - src/main/java/com/stockops/ai/forecast/AiForecastProperties.java (api-key 필드 추가)
  - src/main/java/com/stockops/ai/forecast/AiForecastClient.java (X-API-Key 헤더 주입)
  - src/test/java/com/stockops/ai/bedrock/BedrockAiPropertiesTest.java (신규 - 3 tests)
  - src/test/java/com/stockops/ai/forecast/AiForecastClientTest.java (신규 - 5 tests)
  - src/test/java/com/stockops/ai/forecast/ProphetForecastModelTest.java (신규 - 3 tests)
- Decisions:
  - enabled: false 기본값 - 프로덕션 자격증명 없이 안전한 배포 가능
  - generationModelReference() 헬퍼: inferenceProfileArn > modelId 우선순위
  - Maven/Python 호스트 미설치 → Docker 컨테이너 빌드 방식 확립 (MSYS_NO_PATHCONV=1, //c/ 경로)
- Blockers: 없음
- Verification: mvn -DskipTests compile 성공 (0 errors). 11개 신규 테스트 모두 PASS.
- Next step: Tasks 2-7A (AI Provider 구현체, Facade, Controller)

---

## 2026-06-09 | Phase 1 - Tasks 2-7A (AI Providers, Facades, Controllers)

- Date: 2026-06-09
- Phase: Phase 1 - Spring AI Infrastructure
- Summary: AI 생성 공급자 인터페이스 및 구현체, 프롬프트 빌더, Bedrock 파사드, Chat 컨트롤러 등 전체 AI 레이어 완성.
- Files changed (main):
  - src/main/java/com/stockops/ai/bedrock/dto/BedrockRecommendationExplanationResponse.java (신규)
  - src/main/java/com/stockops/ai/bedrock/dto/BedrockOpsSummaryResponse.java (신규)
  - src/main/java/com/stockops/ai/bedrock/dto/BedrockRagQueryRequest.java (신규)
  - src/main/java/com/stockops/ai/bedrock/dto/BedrockRagQueryResponse.java (신규)
  - src/main/java/com/stockops/ai/bedrock/dto/BedrockAgentInvokeRequest.java (신규)
  - src/main/java/com/stockops/ai/bedrock/dto/BedrockAgentInvokeResponse.java (신규)
  - src/main/java/com/stockops/ai/bedrock/BedrockPromptBuilder.java (신규)
  - src/main/java/com/stockops/ai/provider/AiServiceStatus.java (신규)
  - src/main/java/com/stockops/ai/provider/AiGenerationRequest.java (신규)
  - src/main/java/com/stockops/ai/provider/AiGenerationResponse.java (신규)
  - src/main/java/com/stockops/ai/provider/AiGenerationProvider.java (신규)
  - src/main/java/com/stockops/ai/bedrock/BedrockGenerationProvider.java (신규)
  - src/main/java/com/stockops/ai/gcp/VertexAiGenerationProvider.java (신규)
  - src/main/java/com/stockops/ai/provider/AiProviderFacade.java (신규)
  - src/main/java/com/stockops/ai/bedrock/BedrockAgentRuntimeClientAdapter.java (신규)
  - src/main/java/com/stockops/ai/bedrock/BedrockAiFacade.java (신규)
  - src/main/java/com/stockops/controller/BedrockAiController.java (신규)
  - src/main/java/com/stockops/ai/chat/dto/AiChatRequest.java (신규)
  - src/main/java/com/stockops/ai/chat/dto/AiChatResponse.java (신규)
  - src/main/java/com/stockops/controller/AiChatController.java (신규)
  - src/main/java/com/stockops/service/ai/AIRecommendationService.java (detailRecommendation 메서드 추가)
- Files changed (test):
  - src/test/java/com/stockops/ai/bedrock/BedrockPromptBuilderTest.java (신규 - 3 tests)
  - src/test/java/com/stockops/ai/provider/AiProviderFacadeTest.java (신규 - 4 tests)
  - src/test/java/com/stockops/ai/bedrock/BedrockAiFacadeTest.java (신규 - 2 tests)
  - src/test/java/com/stockops/controller/AiChatControllerTest.java (신규 - 2 tests)
- Decisions:
  - AiProviderFacade: Bedrock 실패 시 Vertex 폴백, 인증 실패 판단은 메시지 키워드 기반
  - chatVisible=false 요청 (추천 설명 등)은 serviceNotice/fallbackNotice 숨김 → UI 오염 방지
  - BedrockPromptBuilder: productName에 이중인용부호 escape 처리
  - BedrockAiFacade: properties.isEnabled()==false 시 "fallback" modelId 즉시 반환
  - BedrockAgentRuntimeClientAdapter: invokeAgent는 pilot stub (KnowledgeBase만 실제 연동)
  - AIRecommendationService.detailRecommendation: readOnly 트랜잭션, ScopeGuard 적용
  - 모든 엔드포인트: @PreAuthorize("@permissionChecker.hasPermission('AI_RECOMMENDATION_READ')")
- Blockers: 없음
- Verification: mvn -DskipTests compile 성공. 테스트 22/22 PASS (BedrockPromptBuilderTest 3, AiProviderFacadeTest 4, BedrockAiFacadeTest 2, AiChatControllerTest 2, + Task1 기존 11 PASS)
- Next step: Task 7B (프론트엔드 Chat 폴백 배너), Task 8 (Agent→AISuggestion 연동), Task 9 (Bedrock Live Smoke Test)
