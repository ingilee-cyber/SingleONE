# 기대 동작(Expected Behavior)

이 문서는 `test-data/demo-full/` 데이터를 실제로 Seed한 뒤, 화면/API에서 무엇이 보여야 하는지를
정리한다. 아래 수치는 실제로 로컬 Backend에 Seed한 뒤 API로 직접 확인한 값이다(2026-08-26 검증).
SingleONE 내부 Filter Rate 자체는 어디에도 적지 않는다.

## Advertiser: aurora-beauty (오로라뷰티) — 대표 시연용(flagship)

### Project: 상시 (2026-05-01 ~ 2026-08-25)

Expected:
* 5개 매체(META/TIKTOK/GOOGLE/NAVER/CRITEO) 전부 표시됨
* 5개 매체 전부 SingleONE Index `VALID` (실측: TIKTOK 118.92 / NAVER 112.43 / CRITEO 98.16 / META 89.09 / GOOGLE 81.39 — 평균 100 부근, 매체 간 점수가 서로 다름)
* 7-day Rolling Index에 변화 존재(요일 효과 + 월별 트렌드로 날짜마다 값이 다름)
* Previous Period 비교 가능(117일 전체 기간 데이터 보유)
* Journey에서 단일/2채널/3채널/4채널 이상/반복 채널/채널 재등장/다중 구매 Path가 모두 존재
* Media Planning Simulation: META/TIKTOK/GOOGLE 3개 매체가 `HIGH` confidence로 예측 가능(실측 확인), NAVER/CRITEO는 `UNAVAILABLE`("데이터 부족") — 아래 "의도적 Simulation Edge Case" 참고
* KPI/Breakdown/Rolling/사용자 여정/채널별 전환 기여도/채널 페어 인사이트가 모두 빈 화면 없이 채워짐(요청 28번 통합 화면 테스트 기준 대표 광고주)
* Media → Campaign(META만 4개, 나머지 매체는 2개) → Ad Group(2개) → Ad(3개) 드릴다운에 전부 데이터 존재(요청 29번 상세 분석 테스트)

의도적 Simulation Edge Case(같은 상시 프로젝트 안에서 매체별로 다르게 설계):
* META/TIKTOK/GOOGLE: 최근 8주 주간 cost가 로그 곡선(`y=a·ln(cost)+b`)을 명확히 따르고 8주 전부 유효 → `HIGH`
* NAVER: 최근 8주 주간 cost 변동폭이 1.2배 미만으로 거의 고정 → 모델 자체가 무효화되어 `UNAVAILABLE`("데이터 부족")
* CRITEO: 최근 8주 중 앞 4주는 cost 자체가 0 → 유효 주차 4주(<6) → `UNAVAILABLE`("데이터 부족")

### Project: 프로모션(7월) / 프로모션(8월)

Expected:
* 각각 7월/8월에만 데이터가 존재하는 전용 캠페인으로 구성(상시 캠페인과 별도)
* 모든 매체가 상시 대비 예산이 커진 ORGANIC 패턴(프로모션 반응이 큰 브랜드 특성)
* Media Planning Simulation은 운영 기간이 8주 후보 window보다 짧아 자연스럽게 `UNAVAILABLE`이 나옴 — 이는 버그가 아니라 "짧은 프로젝트는 예측 불가"를 보여주는 의도된 케이스

## Advertiser: urban-fit (어반핏) — 평범한 기준점

Expected:
* Google/Meta 성과가 상대적으로 강하고, 나머지 매체는 보통 수준(극단적 Edge Case 없음)
* 3개 프로젝트 전부 5개 매체가 정상적으로 SingleONE Index 계산 가능
* Journey에서 GOOGLE 단일 터치, META→GOOGLE 조합이 Top Path 상위를 차지(브랜드별 채널 성향 차별화 확인용)

## Advertiser: living-lab (리빙랩) — Edge Case 전용

### Project: 상시 (실측 확인 완료)

| 매체 | 실측 결과 | 원인 |
|---|---|---|
| NAVER | `VALID` | 정상 |
| CRITEO | `VALID` | 정상 |
| META | `INSUFFICIENT_DATA` | 원시 구매 수가 낮게 설계됨(Cost/운영일은 충분) |
| TIKTOK | `INSUFFICIENT_DATA` | 광고비 총합이 낮게 설계됨(1,000,000 미만) |
| GOOGLE | `INSUFFICIENT_DATA` | 운영일 자체가 5일뿐(7일 미만) — 특정 5개 날짜에만 집행 |

### Project: 프로모션(8월) (실측 확인 완료)

| 매체 | 실측 결과 | 원인 |
|---|---|---|
| NAVER/CRITEO/META/GOOGLE | `VALID` | 정상 |
| TIKTOK | `INSUFFICIENT_DATA` | 구매 수 0으로 설계됨(Cost/노출/클릭은 정상 발생) |

Expected:
* 위 표의 각 매체 상태가 화면(Dashboard)에서 그대로 재현됨
* CPA/ROAS 등 파생 지표가 구매 0인 매체에서 "-" 또는 해당 없음으로 표시됨(제품 자체에 별도 "제외" 상태는 없고 데이터 부족 버킷과 동일하게 처리됨 — 코드 확인 결과)

## Global Advertiser Context 테스트 (요청 27번)

1. `aurora-beauty` 선택 → Dashboard/Project 목록/Journey/Simulation이 모두 aurora-beauty 데이터로 표시
2. `urban-fit`으로 전환 → Dashboard 수치, Project 목록(9개→urban-fit 3개+시스템 기본), Journey 데이터, Simulation 대상 Project가 모두 즉시 변경되고 aurora-beauty의 잔여 데이터가 남지 않음
3. `living-lab`으로 전환 시에도 동일하게 확인

## Journey & Attribution 검증 포인트

* 7일 window 경계: 구매 기준 6일 23시간 전 클릭은 포함, 7일 초과 클릭은 제외 — 각 광고주 Journey 파일에 최소 1건씩 존재
* 연속 동일 채널(예: META→META→GOOGLE) 클릭은 Path 시각화에서는 압축되지만, Attribution/채널 페어 계산은 원본 고유 채널 집합(unique={META,GOOGLE}) 기준으로 0.5/0.5 분배 확인
* 다중 구매 사용자(동일 사용자가 서로 다른 날짜에 2회 이상 구매) 각 광고주 파일에 존재
* 프로젝트 범위 필터링: 상시 프로젝트 선택 시 상시 캠페인 클릭만, 프로모션(7월) 선택 시 해당 캠페인 클릭만 유효 Journey로 집계됨(각 광고주 Journey 데이터의 약 3%가 상시+7월 캠페인이 섞인 사용자로, 이 필터링을 직접 검증할 수 있게 설계됨)

## Invalid Upload Fixture 기대 동작 (요청 21/22번, 실제 Backend로 검증 완료)

| 파일 | 업로드 결과 | 오류 코드 |
|---|---|---|
| performance_missing_required_column.csv | FAILED | `REQUIRED_FIELD_MISSING` (add_to_cart 값이 비어 있음) |
| performance_invalid_date.csv | FAILED | `INVALID_DATE` |
| performance_negative_cost.csv | FAILED | `NEGATIVE_VALUE` |
| performance_non_numeric.csv | FAILED | `INVALID_NUMBER` |
| performance_unsupported_media.csv | FAILED | `UNSUPPORTED_MEDIA` |
| performance_duplicate_inside_file.csv | FAILED | `DUPLICATE_NATURAL_KEY_IN_FILE` |
| performance_duplicate_existing_data.csv | `DUPLICATE_CONFIRMATION_REQUIRED` | 이미 Seed된 aurora-beauty 데이터와 동일한 natural key. Confirm 시 최신 값으로 덮어써짐(SUCCESS), Cancel 시 기존 데이터 유지(CANCELLED) — 두 경로 모두 실제 확인 완료 |
| journey_duplicate_event_id.csv | FAILED | `DUPLICATE_NATURAL_KEY_IN_FILE` |
| journey_invalid_event_type.csv | FAILED | `INVALID_EVENT_TYPE` |
| journey_missing_user_id.csv | FAILED | `REQUIRED_FIELD_MISSING` |
| journey_duplicate_order_id.csv | 아래 "알려진 이슈" 참고 | - |

이 Fixture들은 `seed.mjs`가 정상 Seed 과정에서 자동으로 업로드하지 않는다. 각 파일을 수동으로
업로드해 오류 화면/메시지를 확인하는 용도다.

### 알려진 이슈: journey_duplicate_order_id.csv

이 파일은 원래 "서로 다른 두 PURCHASE 행이 같은 order_id를 가져도 오류가 나지 않는다"는 것을
보여주기 위한 참고용 Fixture다(코드 확인 결과 `order_id`는 어떤 natural key/유일성 검사에도
포함되지 않는다). 실제로 **사전에 Journey 데이터가 없는 새 광고주에 업로드하면 의도한 대로
정상(SUCCESS) 처리됨을 확인했다.**

다만 이미 겹치는 기간의 Journey 데이터가 존재하는 광고주(예: 이 데이터셋을 Seed한 뒤의
aurora-beauty)에 업로드하면, order_id와 무관한 **별도의 기존 Backend 버그**가 발생한다:
기존 데이터와의 중복 여부를 검사하는 쿼리가 ClickHouse `DateTime` 타입 변환에 실패해
`INTERNAL_ERROR`가 발생한다(`Cannot convert string '...' to type DateTime`). 이 버그는
`journey_duplicate_order_id.csv`에 국한되지 않고, **겹치는 기간으로 Journey를 두 번째 업로드하는
모든 경우**에 재현된다(직접 확인: 유효한 CLICK+PURCHASE 1건짜리 파일로도 동일하게 재현됨).

이번 작업 범위는 테스트 데이터 생성이며 제품 코드는 수정하지 않으므로, 이 이슈는 **수정하지 않고
문서화만** 한다. 재현 방법: 이 데이터셋을 Seed한 뒤, `aurora-beauty`에 이 파일(또는 기존 Journey
데이터와 날짜가 겹치는 아무 Journey CSV)을 다시 업로드하면 재현된다.
