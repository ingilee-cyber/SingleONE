# SingleONE 구현 진행 상태

이 문서는 개발 단계별 진행 상태를 기록한다. 상태는 아래 4가지 중 하나로 표시한다.

- 미착수
- 진행 중
- 완료
- 테스트 결과 (통과 / 실패 / 미실행 + 상세 내용)

각 단계를 시작/종료할 때마다 이 문서를 갱신한다. PRD 기준 권장 구현 순서(`docs/SingleONE_PRD.md` 18.2절)를 따른다.

---

## 0. 준비 단계

| 항목 | 상태 |
|---|---|
| PRD → `docs/SingleONE_PRD.md` 변환 및 검증 | 완료 |
| 개발환경 점검 (Java 17 / Node 20+ / npm / Git) | 완료 |
| `CLAUDE.md` 개발 규칙 작성 | 완료 |
| Git 저장소 초기화 및 `.gitignore` | 완료 |

## 0-1. 기본 실행환경 구축 (Backend/Frontend/DB Skeleton)

제품 기능 구현이 아닌, Backend/Frontend/DB가 로컬에서 함께 기동되는 최소 골격을 구축하는 단계.

- 상태: 완료 (아래 "알려진 제약" 1건 제외)
- 생성물: `backend/`(Spring Boot 3.1.5 + Gradle 8.4 Wrapper), `frontend/`(Next.js 15.4.2), `infra/docker-compose.yml`, `.env.example`, `test-data/`
- 테스트/검증 결과:
  - Frontend: `npm run build` 성공, `npm test`(Vitest+RTL 홈 화면 렌더링) 통과, `npm run lint` 통과, `npx playwright test`(e2e/smoke.spec.ts, 실제 브라우저로 Backend 연결 상태 "연결됨" 확인) 통과
  - Backend: `./gradlew compileJava compileTestJava` 성공. `docker compose -f infra/docker-compose.yml up -d`로 MySQL 8.0/ClickHouse 24.8 기동 확인(healthy). `./gradlew bootRun` 실제 기동 확인 — Flyway 마이그레이션 적용, ClickHouseMigrationRunner로 V1 적용(ClickHouse `schema_version` 테이블에 기록됨 확인), `/actuator/health` → `{"status":"UP"}`, `/swagger-ui.html`(→ `/swagger-ui/index.html`, 200) 및 `/v3/api-docs` 정상 응답, CORS preflight가 `http://localhost:3000`에 정확히 허용됨을 curl로 확인.
  - `./gradlew test`(Testcontainers 기반 JUnit)는 **미통과 — 알려진 환경 제약**, 아래 참고.
- 진행 중 실제로 고친 버그 2건 (인프라 자체의 결함이었음, 수정 완료):
  1. `DataSourceConfig`에서 `DataSourceBuilder.create().build()`에 `spring.datasource.*`를 직접 바인딩하면 `url`이 HikariCP의 `setJdbcUrl()`로 매핑되지 않아 "jdbcUrl is required" 오류 발생 → `DataSourceProperties.initializeDataSourceBuilder()`를 거치는 Spring Boot 공식 패턴으로 수정.
  2. MySQL(`@Primary`)과 ClickHouse 두 `DataSource`/`JdbcTemplate` Bean이 공존할 때, `@Primary`는 파라미터명이 일치해도 항상 우선 적용되어 ClickHouse용 Bean 주입 지점(`ClickHouseConfig`, `ClickHouseMigrationRunner`)이 실제로는 MySQL Bean을 받고 있었음 → 각 지점에 `@Qualifier("clickHouseDataSource")`/`@Qualifier("clickHouseJdbcTemplate")`를 명시해 수정.
- 알려진 제약 (환경 문제, 코드 결함 아님):
  - 이 PC의 Docker Desktop(엔진 실제 이름 `dockerDesktopLinuxEngine`, 매우 최신 버전)에서 `docker` CLI/`docker compose`는 정상 동작하지만, Testcontainers(구버전 docker-java 클라이언트 라이브러리 포함)가 사용하는 raw Named Pipe API 접근이 모두 동일한 decoy 400 응답을 받아 Docker 데몬을 찾지 못한다. `DOCKER_HOST`를 `docker_engine`/`dockerDesktopLinuxEngine`/`docker_cli` 각각으로 지정해도 동일하게 실패해, Docker Desktop이 이 버전에서 자체 CLI 이외의 raw API 클라이언트 접근 방식을 바꾼 것으로 추정된다. Testcontainers 기반 자동 테스트(`BackendApplicationTests`)는 코드상 정상이며 위 "실제로 고친 버그 2건"도 이 테스트가 아닌 `bootRun` 수동 기동으로 발견·검증했다. 추후 WSL2 배포판 내부에서 빌드를 실행하거나, Docker Desktop/Testcontainers 업데이트로 호환성이 회복되면 재시도 필요.
  - Next.js 15.4.2에 CVE-2025-66478(RCE, CVSS 10.0) 존재. 공식 패치는 15.4.8이지만, 사용자가 "PRD 명시 버전(15.4.2) 유지"를 선택함 (로컬 개발 전용, 외부 미노출 전제). **외부에 배포/노출하기 전 반드시 재검토 필요.**

## 1. DB Migration 및 Master/Upload 도메인 구축

- 상태: 완료
- 생성물:
  - MySQL Flyway `V2`~`V5`: `advertiser`, `campaign_master`, `ad_group_master`, `ad_master`, `project`, `project_campaign`, `upload_batch`, `upload_error`, `internal_media_filter`(+PRD 8.3 고정 필터율 5건 시드)
  - ClickHouse 자체 러너 `V2`~`V3`: `performance_fact`, `journey_event` (PRD 11.2/9.5 필드 그대로)
  - JPA Entity/Repository 9종 (`domain.advertiser/master/project/upload/filter/common` 패키지) — 순수 데이터 접근 계층만, 서비스/컨트롤러 없음
- 테스트 결과:
  - `ClickHouseMigrationRunnerTest`(순수 단위 테스트, Docker 불필요) 통과
  - `MasterProjectUploadRepositoryTest`(Testcontainers 기반)는 1단계와 동일한 환경 제약으로 `./gradlew test` 자동 실행은 안 됨 — 대신 `bootRun` 후 `docker exec`로 MySQL 9개 테이블/컬럼/제약조건, ClickHouse 2개 테이블 스키마, 필터율 시드값 5건을 모두 수동 대조하여 확인함. Hibernate `ddl-auto: validate`가 기동 시 모든 Entity-테이블 매핑을 검증하는데 오류 없이 기동됨.
- 진행 중 발견해 고친 버그 1건: `ClickHouseMigrationRunner`가 마이그레이션 SQL 파일이 주석 줄로 시작하면 전체 블록을 주석으로 오인해 실행하지 않으면서도 "적용 완료"로 잘못 기록하는 버그가 있었음. `V2__performance_fact.sql`/`V3__journey_event.sql`이 실제로 실행되지 않았는데도 `schema_version`에는 기록되어 있던 것을 `docker exec` 수동 확인 중 발견. 주석 제거 로직을 줄 단위로 수정하고, 회귀 방지용 단위 테스트 추가. ClickHouse의 잘못된 `schema_version` 기록은 초기화 후 재기동으로 정정함.

## 2. 성과/Journey 업로드 검증·Batch 상태·ClickHouse 적재

- 상태: 완료
- 생성물 (`backend/src/main/java/com/singleone/backend/upload/`):
  - 파일 파싱: CSV(Apache Commons CSV)/XLSX(POI + excel-streaming-reader, PRD 11.11 스트리밍) 공통 `RowSource` 추상화
  - 검증: `PerformanceRowParser`/`JourneyRowParser` — PRD 11.5 규칙(필수 컬럼, 날짜/숫자 형식, 음수 금지, 지원 media, 파일 내 natural key 중복) + "업로드 요청 광고주와 행의 advertiser_id 일치"(사용자 확인 사항) 검증
  - 상태 머신: `UploadService`/`UploadProcessor` — PRD 11.7(VALIDATING→FAILED/DUPLICATE_CONFIRMATION_REQUIRED/IMPORTING→SUCCESS/CANCELLED) 그대로 구현, PRD 11.8 비동기 처리는 Spring `@Async` 전용 스레드풀로 구현(PRD 기술 스택에 없는 메시지 큐 등 추가 인프라 도입하지 않음)
  - 중복 확인: PRD 11.6 — 기존 SUCCESS 데이터와 natural key 겹침 확인 후 사용자 확인 대기, confirm-overwrite/cancel API(PRD 13.4) 구현
  - Master Upsert: PRD 11.10/5.3 — ClickHouse `argMax`로 배치별 최신 이름/날짜를 집계해 Advertiser/Campaign/AdGroup/Ad Master에 반영 ("최신 date, 동일 date면 최신 SUCCESS batch" 규칙)
  - REST API: `POST /api/v1/uploads/performance`, `/journey`, `GET /api/v1/uploads`(페이지네이션 기본 50/최대 200), `GET /api/v1/uploads/{id}`, `GET /api/v1/uploads/{id}/errors`, `POST /api/v1/uploads/{id}/confirm-overwrite`, `/cancel`
  - MySQL 마이그레이션 `V6`(advertiser에 latest_source_date/latest_upload_batch_id 추가 — 1단계에서 누락됐던 부분), ClickHouse 마이그레이션 `V4`(performance_fact에 advertiser_name/campaign_name/ad_group_name/ad_name 추가 — 역시 1단계 누락분)
- 테스트 결과:
  - 단위 테스트 18개 전부 통과 (Docker 불필요): `PerformanceRowParserTest`(6), `JourneyRowParserTest`(5), `CsvRowSourceTest`(2), `TimeUtilsTest`(3), `ClickHouseMigrationRunnerTest`(2)
  - Backend: `./gradlew build -x test` 성공. `./gradlew test`(Testcontainers 포함 전체)는 1단계와 동일한 Docker Desktop 환경 제약으로 자동 실행 불가.
  - **실제 업로드 API를 curl로 직접 호출해 전체 파이프라인을 수동 End-to-End 검증함**: 정상 업로드→SUCCESS, Master Upsert 결과(광고주/캠페인/광고그룹/광고 이름·최신일자) 정확함, 동일 파일 재업로드→DUPLICATE_CONFIRMATION_REQUIRED, confirm-overwrite→SUCCESS, cancel→CANCELLED, 검증 오류(음수 cost, 미지원 media)→FAILED + 정확한 오류 목록, 광고주 불일치 검증, 목록 페이지네이션 모두 확인.
  - Frontend: `npm run build`(타입체크 포함), `npm test` 통과 (이번 단계에서 Frontend 코드 변경 없음, 회귀 없음 재확인).
- 진행 중 발견해 고친 버그 4건 (전부 위 수동 E2E 테스트로 발견 — Testcontainers를 못 쓰는 상황에서 이 수동 검증이 없었다면 놓쳤을 것들):
  1. ClickHouse read 쿼리에서 `?` PreparedStatement 파라미터 사용 시 `ArrayIndexOutOfBoundsException` 발생 (clickhouse-jdbc 0.9.0 드라이버가 집계 함수 포함 SELECT의 파라미터 바인딩을 잘못 처리함, INSERT의 `?` 바인딩은 정상). → 내부에서 생성한 안전한 값(Long ID, 검증된 advertiser_id, 날짜)에 한해 값을 직접 문자열로 조합하는 방식으로 우회.
  2. 위 우회 과정에서 `max(date) AS date`처럼 별칭을 원본 컬럼명과 동일하게 지어 ClickHouse가 "집계 함수 안에 집계 함수" 오류를 냄. → 별칭을 `latest_date`로 변경.
  3. 기존 SUCCESS 배치가 없을 때 불변(`Set.of()`) 빈 Set을 반환했는데, 호출부에서 `retainAll()`로 수정하려다 `UnsupportedOperationException` 발생. → 가변 `HashSet`으로 변경.
  4. **가장 심각한 버그**: 중복 감지용 natural key 문자열 구성이 `PerformanceRow.naturalKey()`(date+advertiser_id+media+campaign_id+ad_group_id+ad_id)와 `findExistingSuccessNaturalKeys()`(advertiser_id 누락, 5개 필드만)가 서로 달라 두 값이 절대 일치하지 않았음. 그 결과 동일 파일을 몇 번을 재업로드해도 중복이 전혀 감지되지 않고 계속 SUCCESS 처리됨 (PRD 11.6 위반). 실제로 같은 파일을 두 번 업로드해보는 수동 테스트로만 발견됨. → 두 곳의 키 구성을 동일하게 맞춤.
- 알려진 제약: `./gradlew test`의 Testcontainers 자동 테스트는 1단계에서 기록한 것과 동일한 이유(이 PC의 Docker Desktop 버전과 구버전 docker-java 클라이언트 라이브러리 간 named pipe API 비호환)로 여전히 자동 실행되지 않음. 이번 단계는 실제 API를 직접 호출하는 수동 E2E로 이를 보완했다.

### 2-1. 스키마/복합 identity 검증용 JUnit/Testcontainers 테스트 보강

- 상태: 완료
- 생성물: `MasterProjectUploadRepositoryTest`에 3개 테스트 추가(AdGroup/Ad 복합키, ProjectCampaign PK 제약 — PRD 5.1 동일 캠페인 같은 프로젝트 내 중복 금지/다른 프로젝트 중복 허용, AdGroupMaster→CampaignMaster FK 위반), 신규 `ClickHouseSchemaTest`(performance_fact/journey_event 컬럼 구성, Journey CLICK/PURCHASE nullable 필드 라운드트립)
- 테스트 결과: `./gradlew test`는 동일한 Docker Desktop 제약으로 자동 실행 불가. 대신 `docker exec`로 각 테스트가 검증하려는 내용을 MySQL/ClickHouse에 직접 재현해 전부 확인함 — AdGroup/Ad 복합키 저장·조회 정상, 동일 캠페인이 다른 프로젝트에는 추가되고(`project_campaign`) 같은 프로젝트에서는 `Duplicate entry ... PRIMARY` 오류로 거부됨, 존재하지 않는 캠페인을 참조하는 AdGroupMaster는 `foreign key constraint fails` 오류로 거부됨, ClickHouse 두 테이블의 컬럼 구성이 테스트 기대값과 정확히 일치, CLICK/PURCHASE 행의 nullable 필드가 기대대로 저장됨.

### 2-2. 업로드 플로우 통합 테스트 + 데이터 관리 화면(Frontend)

업로드 규칙(PRD 11장) 자체는 1~2단계에서 이미 구현되어 있어 다시 만들지 않았다. 이번에는 그동안 자동화되지 않았던 두 부분만 채웠다.

- 상태: 완료
- 생성물:
  - Backend: 신규 `UploadFlowIntegrationTest`(`backend/src/test/java/com/singleone/backend/upload/`) — 정상 Performance CSV/XLSX 업로드+Master Upsert, 정상 Journey CSV 업로드, 기존 SUCCESS 데이터와 duplicate → confirm-overwrite, duplicate → cancel(ClickHouse 데이터 삭제 확인), 오류 행 포함 파일 → FAILED(row-specific error, ClickHouse 미반영 확인) 총 7개 시나리오. 행 단위 검증(필수 컬럼/날짜/숫자/음수/미지원 media/파일 내 중복)은 기존 `PerformanceRowParserTest`/`JourneyRowParserTest`가 이미 다루고 있어 중복 작성하지 않음.
  - Frontend: 신규 `frontend/app/uploads/page.tsx`(데이터 관리 화면 — 업로드 폼, 2초 간격 상태 폴링, 업로드 이력 표, 오류 상세 다이얼로그, 중복 확인/취소 버튼), `frontend/lib/uploadApi.ts`(Backend DTO와 1:1 대응하는 API 클라이언트), `frontend/app/uploads/page.test.tsx`(Vitest+RTL 4개 테스트). 홈 화면(`frontend/app/page.tsx`)에 데이터 관리 화면으로 가는 링크 1줄 추가.
  - REST API/OpenAPI 계약은 기존 컨트롤러 그대로 사용(springdoc이 `/v3/api-docs`를 실시간 생성하므로 별도 작성 불필요).
- 테스트 결과:
  - Backend: `./gradlew compileTestJava` 성공. `./gradlew test --tests UploadFlowIntegrationTest`는 이 PC의 Docker Desktop/Testcontainers 비호환(1~2단계와 동일)으로 자동 실행 불가 — `bootRun` + curl로 7개 시나리오 전부 수동 재현해 통과 확인(Master Upsert 결과는 `docker exec` MySQL 조회로 재확인). 기존 18개 단위 테스트 재실행해 회귀 없음 확인.
  - Frontend: `npm test`(Vitest, 신규 4개 포함 총 5개) 통과, `npm run build`(타입체크 포함) 성공, `npm run lint` 통과. 추가로 실제 Backend를 띄운 상태에서 브라우저(Playwright 스크립트, 커밋 대상 아님)로 업로드 → 성공 표시, 재업로드 → 중복 확인 → 확인(덮어쓰기), 오류 파일 → 오류 상세 다이얼로그까지 골든 패스를 직접 확인함.
- 진행 중 발견해 고친 버그 2건:
  1. **Frontend**: 업로드 성공 후 파일 선택 상태를 `null`로 초기화했는데, 브라우저 `<input type="file">`의 실제 값은 그대로 남아 있어 사용자가 "동일한 파일"을 다시 선택해도 `change` 이벤트가 발생하지 않는 경우가 있었다(중복 업로드 테스트 시나리오, PRD 11.6에서 실제로 필요한 사용자 흐름). → 업로드 성공/실패 시 입력 요소에 `key`를 갱신해 완전히 새로 마운트되도록 수정.
  2. **Frontend 테스트 인프라**: `vitest.config.ts`가 `e2e/`의 Playwright 스펙까지 테스트 대상으로 잡아 `npm test`가 실패했고(1단계 이후 잠재해 있던 문제), RTL 컴포넌트 테스트 간 DOM cleanup이 없어 여러 테스트를 한 파일에 작성하면 이전 렌더링이 남아 요소를 중복으로 찾는 문제가 있었다. → `vitest.config.ts`에 `e2e/**` 제외 추가, `vitest.setup.ts`에 `afterEach(cleanup)` 추가.
  3. **환경**: 이 PC의 MySQL 컨테이너 데이터 볼륨이 이전 `.env` 비밀번호로 초기화되어 있어 최신 `.env`의 `MYSQL_PASSWORD`와 불일치해 `bootRun`이 연결 실패했다. 로컬 개발용 디스크 데이터라 볼륨을 재생성해 해결(운영 데이터 아님).

## 3. SingleONE 필터/Index 계산 서비스 + Golden Automated Tests

- 상태: 완료 (Backend 계산 엔진만. Dashboard UI/REST API는 이번 단계 범위 밖 — 사용자 지시)
- 생성물 (`backend/src/main/java/com/singleone/backend/analytics/`):
  - 계산 로직과 DB 접근을 분리한 구조: `SingleOneIndexCalculator`(PRD 8.4/8.5/8.6, Spring/DB에 의존하지 않는 순수 계산 — `MathContext.DECIMAL128` 사용)가 `MediaPerformanceTotals`/`DailyMediaTotal`/`SingleOnePerformance`/`IndexStatus`/`MediaIndexResult`를 다룸. `PerformanceAggregationRepository`(ClickHouse 일자×매체 집계, natural key 중복은 `argMax(..., upload_batch_id)`로 최신 SUCCESS batch만 사용 — PRD 11.6)와 `SingleOnePerformanceService`(프로젝트 캠페인 조회 → 집계 → 계산 오케스트레이션, PRD 8.8 이전 기간 비교/8.9 7일 Rolling Index)로 이어짐.
  - `ProjectCampaignRepository`에 `findByIdProjectId` 메서드 추가.
  - 필터율(`internal_media_filter`, Stage 1)은 어떤 DTO/API에도 노출하지 않음 — 이번 단계는 REST API 자체가 없어 자동으로 충족됨.
- 해석이 필요했던 부분(계획 승인 시 사용자에게 명시): "필수 데이터 누락"은 프로젝트 포함 매체인데 기간 내 성과 원본이 전혀 없는 경우, "데이터 부족"은 원본은 있으나 최소 조건(운영일/Cost/구매) 미달인 경우로 구분. cost=0 매체는 CPA/ROAS 모두 계산 불가(null) 처리(PRD는 purchases=0만 명시). AC-11~13(캠페인/광고그룹/광고 계층 상세)은 이후 "매체 상세" 단계 범위로 제외.
- 테스트 결과:
  - **`SingleOneIndexCalculatorTest`(순수 JUnit, Docker 불필요, 8개 전부 통과)**: PRD 15.3 Golden Dataset을 그대로 fixture로 넣어 Google 133.525931/Meta 108.884222/TikTok 99.183914/Naver 90.429307/Criteo 67.976627, 평균 100.000000이 **정확히** 재현됨을 확인(AC-02), UI 반올림 134/109/99/90/68 확인(AC-03). 이 외 AC-04(2개 유효 매체 평균 100), AC-05(Cost 미달), AC-06(구매 10 미만, 내부 소수값 9.76 기준), AC-07(구매 0 → CPA null/ROAS 0), AC-08(필수 데이터 누락), AC-09(비교 가능 매체 부족), AC-10(운영일 6일 vs 7일 경계) 전부 통과.
  - `PerformanceAggregationRepositoryTest`/`SingleOnePerformanceServiceTest`(Testcontainers)는 이 PC의 Docker Desktop 비호환으로 자동 실행 불가(기존 단계와 동일 증상). 대신 임시 `CommandLineRunner`(검증 후 삭제)로 `bootRun` 위에서 4개 시나리오를 실제 Spring Bean으로 직접 실행해 확인: 동일 natural key가 2개 SUCCESS batch에 있을 때 최신 batch 값만 집계(2000/20, 합산 아님), cost=0인 날짜가 별도 행으로 정확히 반환됨, 이전 기간 비교에서 GOOGLE만 조건 미달로 INSUFFICIENT_DATA 처리되고 나머지 매체 평균은 여전히 100(AC-14), 7일 Rolling에서 특정 window에 결측 매체가 있는 날짜(5/8, 5/9)는 결과에서 제외되고 이후 날짜(5/10~5/14)는 선택 기간 이전 데이터까지 사용해 정상 산출되며 평균 100 유지(AC-15/AC-16). 검증에 사용한 테스트 데이터는 정리함.
  - 기존 회귀 테스트(`common.time`, `upload.*`, `migration.clickhouse`) 전부 재실행해 통과 확인.

## 4. 프로젝트(Project) CRUD 및 캠페인 Selection

- 상태: 미착수
- 테스트 결과: 미실행

## 5. Dashboard

- 상태: 미착수
- 테스트 결과: 미실행

## 6. 매체/캠페인/광고 그룹/광고 상세

- 상태: 미착수
- 테스트 결과: 미실행

## 7. Journey & Attribution

- 상태: 미착수
- 테스트 결과: 미실행

## 8. Media Planning Simulation Model 및 UI

- 상태: 미착수
- 테스트 결과: 미실행

## 9. Playwright 핵심 E2E 및 Edge Acceptance Criteria 완료

- 상태: 미착수
- 테스트 결과: 미실행

---

## Acceptance Criteria 커버리지 (AC-01 ~ AC-55)

각 AC가 어느 단계에서 검증되었는지 구현이 진행되면서 아래에 채운다 (현재는 전부 미착수).

| AC 범위 | 관련 단계 | 상태 |
|---|---|---|
| AC-01, AC-14~AC-16, AC-23~AC-24 | Dashboard / 상세 | 미착수 |
| AC-02~AC-13 | Index 계산 | 미착수 |
| AC-17~AC-22 | Project | 미착수 |
| AC-25 | 필터율 비공개 | 미착수 |
| AC-26~AC-33 | 업로드 | 미착수 |
| AC-34~AC-42 | Journey & Attribution | 미착수 |
| AC-43~AC-52 | Media Planning Simulation | 미착수 |
| AC-53 | Simulation 비저장 | 미착수 |
| AC-54 | Pagination | 미착수 |
| AC-55 | Journey Top 20 | 미착수 |
