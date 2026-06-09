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
- Actual result: NOT_RUN
- Status: NOT_RUN

### TS-P0-002

- ID: TS-P0-002
- Feature: Prophet Model Cache LRU eviction
- Test type: Unit
- Preconditions: ProphetModelCache 클래스 존재
- Steps: max_size=2로 캐시 생성 → set(1), set(2), get(1), set(3) → get(2)
- Expected result: get(2)는 None (LRU 퇴출), get(1)과 get(3)은 유효
- Actual result: NOT_RUN
- Status: NOT_RUN

### TS-P0-003

- ID: TS-P0-003
- Feature: forecasting._fill_missing_dates
- Test type: Unit
- Preconditions: services/forecasting.py 존재
- Steps: [2026-06-01, 2026-06-03] 날짜 데이터로 fill_missing_dates 호출
- Expected result: [5.0, 0.0, 7.0]
- Actual result: NOT_RUN
- Status: NOT_RUN

### TS-P0-004

- ID: TS-P0-004
- Feature: forecast_async 결과 구조
- Test type: Unit
- Preconditions: FakeModel monkeypatching 가능
- Steps: fake_train monkeypatch → forecast_async(product_id=1, days=2)
- Expected result: product_id=1, days=2, forecast[0].yhat=10.25
- Actual result: NOT_RUN
- Status: NOT_RUN

### TS-P0-005

- ID: TS-P0-005
- Feature: /predict/bulk 부분 실패
- Test type: API
- Preconditions: TestClient 가용, product_id=2가 ValueError 발생하도록 monkeypatch
- Steps: POST /predict/bulk {"products": [{"product_id":1,"days":1},{"product_id":2,"days":1}]}
- Expected result: HTTP 400, detail.successful=1, "product_id=2" in errors
- Actual result: NOT_RUN
- Status: NOT_RUN
