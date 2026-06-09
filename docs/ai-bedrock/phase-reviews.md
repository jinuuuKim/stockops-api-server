# StockOps AI Phase Reviews

## Review Template

- Date:
- Phase:
- Plan alignment:
- Code review notes:
- Tests run:
- Browser hands-on status:
- Blocked browser scenarios:
- Alternative tests completed:
- Failures:
- Risks:
- Decision:
- Phase status:

---

## 2026-06-09 | Documentation Task Review

- Date: 2026-06-09
- Phase: Documentation Task
- Plan alignment: 계획서 템플릿 그대로 3개 파일 생성. 추가 누락 없음.
- Code review notes: 코드 변경 없음. 문서 구조 일치.
- Tests run: 없음 (문서 생성만)
- Browser hands-on status: 해당 없음
- Blocked browser scenarios: 없음
- Alternative tests completed: 없음
- Failures: 없음
- Risks: 없음
- Decision: 완료
- Phase status: accepted

---

## 2026-06-09 | Phase 0 Review

- Date: 2026-06-09
- Phase: Phase 0 - FastAPI/Prophet Forecast Service Stabilization
- Plan alignment: 계획서 모든 스텝 완료. .env.example은 이미 완비되어 있어 수정 불필요.
- Code review notes: 기존 main.py, services/forecasting.py, models/prophet_model.py 수정 없음. 테스트만 추가됨. FakeModel이 predict_future 시그니처(make_future_dataframe + predict)와 정확히 일치.
- Tests run: pytest 18/18 PASS (Docker exec stockops-ai-module)
  - test_api_contract.py: 13 passed
  - test_forecasting_service.py: 2 passed
  - test_prophet_model_cache.py: 3 passed
- Browser hands-on status: 해당 없음 (Python 서비스, UI 없음)
- Blocked browser scenarios: 없음
- Alternative tests completed: 없음
- Failures: 없음
- Risks: Python이 호스트에 없어 Docker exec으로 실행. 향후 호스트 Python 설치 시 재검증 필요.
- Decision: 완료
- Phase status: accepted

---

## 2026-06-09 | Phase 1 - Task 1 Review

- Date: 2026-06-09
- Phase: Phase 1 - Spring AI Infrastructure (Task 1)
- Plan alignment: pom.xml SDK 의존성, application.yml 설정 블록, 프로퍼티 클래스 2개, AiForecastClient API-Key 주입 완료.
- Code review notes:
  - BedrockAiProperties.generationModelReference(): inferenceProfileArn 우선순위 올바름
  - AiForecastClient: apiKey blank 체크 후 헤더 추가 — null-safe
  - 기존 GeminiAiProvider (ExternalAiProvider 구현) 수정 없음 — 신규 AiGenerationProvider와 공존
- Tests run: 11/11 PASS (mvn test) — BedrockAiPropertiesTest 3, AiForecastClientTest 5, ProphetForecastModelTest 3
- Browser hands-on status: 해당 없음 (설정 레이어)
- Blocked browser scenarios: 없음
- Alternative tests completed: 없음
- Failures: 없음
- Risks: google-genai 1.5.0 vertexAI() 빌더 메서드명 불확실 — 실제 SDK 연동 시 재검증 필요
- Decision: 완료
- Phase status: accepted

---

## 2026-06-09 | Phase 1 - Tasks 2-7A Review

- Date: 2026-06-09
- Phase: Phase 1 - Spring AI Infrastructure (Tasks 2~7A)
- Plan alignment: 계획서 Tasks 2(DTOs+PromptBuilder), 3(Providers), 3A(AiProviderFacade), 4(BedrockAiFacade), 5(BedrockAgentRuntimeClientAdapter), 6(BedrockAiController), 7A(AiChatController) 완료.
- Code review notes:
  - BedrockPromptBuilder: sanitize()로 이중인용부호·백슬래시 escape → JSON 주입 방지
  - AiProviderFacade: chatVisible=false 요청에 serviceNotice/fallbackNotice "" 반환 → UI 오염 방지
  - BedrockAiFacade.buildOpsFacts: limit(20)으로 대량 추천 데이터 LLM 전달 제한
  - AIRecommendationService.detailRecommendation: readOnly, ScopeGuard 적용 — 기존 패턴 일치
  - BedrockAgentRuntimeClientAdapter.invokeAgent: pilot stub — KnowledgeBase만 실제 연동
  - AiChatController: @PreAuthorize 적용, AiGenerationRequest.chatVisible=true 고정
- Tests run: 22/22 PASS (mvn test) — BedrockPromptBuilderTest 3, AiProviderFacadeTest 4, BedrockAiFacadeTest 2, AiChatControllerTest 2 + Task1 11
- Browser hands-on status: BLOCKED_ENVIRONMENT (Bedrock 자격증명 없음)
- Blocked browser scenarios: TS-P1-010, TS-P1-011
- Alternative tests completed: Unit 테스트로 비활성화 폴백 응답 검증 완료
- Failures: 없음
- Risks:
  - google-genai SDK vertexAI() 메서드: 컴파일은 성공하나 런타임 연동은 미검증
  - invokeAgent: KnowledgeBase 실제 응답 파싱은 Bedrock 자격증명 환경에서 추가 검증 필요
- Decision: 완료 (미검증 브라우저 시나리오는 Bedrock 자격증명 환경에서 후속 검증)
- Phase status: accepted
