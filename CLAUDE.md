# SingleONE 개발 규칙 (Claude Code)

이 문서는 SingleONE 테스트 제품을 개발할 때 항상 지켜야 하는 규칙이다.

## Source of Truth

- 제품 요구사항의 유일한 근거 문서는 **[`docs/SingleONE_PRD.md`](docs/SingleONE_PRD.md)** 이다.
- `docs/SingleONE_PRD.md`는 원본 `docs/SingleONE_PRD_v1.0_20260812.docx`를 손실 없이 변환한 사본이다. 두 문서가 어긋나 보이면 `.docx` 원본을 기준으로 `.md`를 다시 검증하고, 임의로 `.md`만 고치지 않는다.
- 요구사항, 계산식, Acceptance Criteria(AC-01~AC-55)는 `docs/SingleONE_PRD.md`를 직접 참조한다. 이 파일에는 요약을 다시 옮겨 적지 않는다.
- **PRD에서 명확하지 않은 사항을 발견하면 임의로 구현 방식을 정하지 말고 반드시 사용자에게 질문한다.**

## Hard Rules (절대 위반 금지)

1. PRD에 없는 기능을 임의로 추가하지 않는다.
2. 기술 스택 버전(Java 17, Spring Boot 3.1.5, Gradle 8.4, Node.js 20+, TypeScript 5, Next.js 15.4.2, React 19, MUI 7 등)을 임의로 업그레이드하거나 변경하지 않는다.
3. 과거 명칭인 "비교 그룹"을 사용하지 않는다. `Project`/`프로젝트`만 사용한다.
4. `group_id`/`group_name`을 만들지 않는다. `project_id`/`project_name`만 사용한다.
5. 저장된 시나리오(Saved Scenario), Snapshot 기능을 만들지 않는다.
6. Media Planning Simulation에 자동 예산 추천, 자동 배분, 최적 예산 계산 기능을 만들지 않는다.
7. 내부 SingleONE 필터율(media filter rate)을 API 응답이나 Frontend에 노출하지 않는다.
8. SingleONE Index 점수는 매체(Media) 레벨에서만 계산한다. 캠페인/광고그룹/광고 레벨에는 계산하지 않는다.
9. SingleONE 성과(Dashboard 등 성과 집계)와 Journey & Attribution 성과를 같은 데이터 풀로 합치지 않는다.
10. MySQL은 운영/관리 데이터(Advertiser, Project, Master, Upload 등), ClickHouse는 대용량 분석 데이터(Performance Fact, Journey Event 등)로 역할을 분리한다.
11. JPA/Hibernate는 MySQL에만 사용한다. ClickHouse는 별도 Client/JDBC로 접근한다.
12. `SUCCESS` 상태의 `upload_batch`만 분석(Dashboard/Index/Journey)에 사용한다.
13. SingleONE 관련 계산(필터링, Index, Attribution 등)은 `BigDecimal` 기반으로 처리한다. 화면에서만 반올림하고 내부 계산 정밀도를 유지한다.
14. Backend/DB의 Timestamp 저장 기준은 UTC이다. timezone 정보가 없는 입력값은 Asia/Seoul로 해석하고, UI 표시도 Asia/Seoul 기준으로 한다.
15. OpenAPI 문서를 Frontend/Backend API 계약의 Source of Truth로 유지한다. API 변경 시 OpenAPI 문서도 함께 최신화한다.
16. 기능 구현 후에는 관련 자동 테스트를 반드시 실행한다.
17. 테스트가 실패한 상태에서 다음 기능 구현으로 넘어가지 않는다.
18. 오류를 숨기거나, 테스트를 임의로 스킵/수정해서 강제로 통과시키지 않는다. 실패 원인을 파악해 실제로 고친다.
19. 모든 작업은 간결하게 처리하고, 최소화할 수 있는 코드는 최소화한다. 불필요한 추상화, 중복 코드, 당장 필요하지 않은 기능을 미리 만들지 않는다.

## 진행 방식

- 각 개발 단계의 진행 상태는 [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md)에 기록하고 최신 상태로 유지한다.
- 큰 구조적 결정(DB 스키마, API 계약, 라이브러리 선택 등)은 진행 전에 사용자에게 확인한다.
- 위 Hard Rule과 충돌하는 요청을 받으면 바로 구현하지 말고, 충돌 지점을 설명하고 확인을 구한다.
