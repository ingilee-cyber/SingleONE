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

- 상태: 완료
- 생성물:
  - Backend `backend/src/main/java/com/singleone/backend/project/`: `ProjectService`(생성/수정/삭제, 검증 규칙, "전체 캠페인" 시스템 기본 프로젝트 관리), `ProjectController`(REST API 5종), `ProjectRequestException`/`ProjectExceptionHandler`(`UploadRequestException` 패턴 재사용), DTO(`ProjectResponse`/`ProjectUpsertRequest`/`CampaignSelection`/`CampaignOptionResponse`).
  - REST API: `GET/POST /api/v1/advertisers/{advertiserId}/projects`(검색/정렬/페이지네이션 기본 50·최대 200), `PUT/DELETE /api/v1/projects/{projectId}`, `GET /api/v1/advertisers/{advertiserId}/campaigns`(PRD 5.3 캠페인명/ID 검색+매체 필터, 프로젝트 생성/수정용).
  - "전체 캠페인" 시스템 기본 프로젝트(PRD 5.2)는 `project` 테이블에 광고주당 1행만 저장하고(첫 프로젝트 목록 조회 시 자동 생성), 포함 캠페인은 저장하지 않고 그 광고주의 `campaign_master` 전체를 항상 동적으로 계산한다. 새 캠페인이 업로드되면 별도 동기화 없이 자동으로 포함된다.
  - Stage 3 `SingleOnePerformanceService`가 `ProjectCampaignRepository`를 직접 쓰던 것을 `ProjectService.resolveIncludedCampaigns`로 교체해, 전체 캠페인 프로젝트도 Index 계산에 사용할 수 있도록 연동함.
  - MySQL Flyway `V7`: `project_campaign`의 FK를 `ON DELETE CASCADE`로 변경(프로젝트 삭제 시 하위 캠페인 선택 행도 함께 삭제).
  - Frontend `frontend/app/projects/page.tsx`(+`frontend/lib/projectApi.ts`): 프로젝트 목록(매체 Chip, 캠페인 수, 시스템 기본 배지), 생성/수정 공용 Dialog(캠페인 검색+매체 필터+체크박스 선택, 선택 매체 수 실시간 표시로 최소 2개 조건을 저장 전에 안내), 삭제 확인 Dialog. 홈 화면에 `/projects` 링크 추가.
- 테스트 결과:
  - Backend `ProjectServiceTest`(Testcontainers)는 이 PC의 Docker Desktop 비호환으로 자동 실행 불가(기존 단계와 동일 증상). 대신 `bootRun` + curl로 전체 시나리오를 직접 재현해 통과 확인: 전체 캠페인 프로젝트 자동 생성 및 동적 캠페인 포함, 정상 생성, 이름 중복 거부, 매체 1개만 선택 시 거부(AC-17), 존재하지 않는 캠페인 거부, 이름 중복 재확인, 수정 시 project_id 유지하며 캠페인 전체 교체, 시스템 기본 프로젝트 수정/삭제 거부(AC-21), 일반 프로젝트 삭제 시 하위 project_campaign 함께 삭제(V7 마이그레이션 확인), 페이지 크기 300 요청 시 200으로 clamp. 캠페인 재사용(AC-19)은 서로 다른 project_id로 각각 정상 생성됨을 확인. `resolveIncludedCampaigns`가 전체 캠페인 프로젝트에 대해 실제 캠페인 3개를 정확히 반환함도 임시 CommandLineRunner(검증 후 삭제)로 확인.
  - 기존 회귀 테스트(`common.time`, `upload.*`, `migration.clickhouse`, `analytics.SingleOneIndexCalculatorTest`) 전부 재실행해 통과 확인.
  - Frontend: `npm test`(신규 4개 포함 총 9개) 통과, `npm run build`(타입체크 포함), `npm run lint` 통과. 실제 Backend/Frontend를 띄운 상태에서 브라우저(Playwright 스크립트, 커밋 대상 아님)로 전체 캠페인 프로젝트 확인 → 새 프로젝트 생성(매체 2개 미만 시 저장 버튼 비활성화 확인) → 수정 → 삭제 확인 모달 → 삭제까지 골든 패스를 직접 확인함.
- 진행 중 발견해 고친 사소한 문제 1건: "새 프로젝트" 버튼 텍스트가 줄바꿈되는 스타일 문제를 화면 스크린샷 확인 중 발견해 `whiteSpace: nowrap`으로 수정함.

## 5. Dashboard

- 상태: 완료
- 생성물:
  - Backend: Stage 3 `SingleOneIndexCalculator`/`SingleOnePerformanceService`를 그대로 재사용하도록 확장(새 계산 없음, 기존에 구하고 버리던 중간값을 응답에 포함).
    - `MediaIndexResult`에 `rawTotals`(원본 성과), `rawPerformance`(원본 CPA/ROAS, PRD 8.7), `components`(Index 4개 구성요소 개별 지수, PRD 6.3 Breakdown) 추가.
    - `SingleOneIndexCalculator.aggregateProjectTotals`: 매체별 값을 합산만 하는 순수 함수(KPI 카드용).
    - `SingleOnePerformanceService`에서 중복된 `advertiserId` 파라미터 제거(Project 엔티티에서 직접 읽음 — projectId와 advertiserId 불일치 가능성 제거).
    - 신규 패키지 `com.singleone.backend.dashboard`: `DashboardService`(이전 기간·rolling·합계 조합, Index 점수 내림차순 정렬), `DashboardController`(`GET /api/v1/projects/{projectId}/dashboard?from=&to=`). 존재하지 않는 프로젝트는 기존 `ProjectRequestException`/`ProjectExceptionHandler`(Stage 4)를 재사용해 400 응답.
  - Frontend `frontend/app/dashboard/`(Material UI + Apache ECharts):
    - 필터: 광고주 ID, 프로젝트 Select(Stage 4 API), 기간 Quick option 5종(최근 7일/30일/이번 달/지난 달/직접 설정) + 이전 기간 비교 스위치(기본 ON), 광고주 변경 시 프로젝트 재조회 및 무효 선택 해제(PRD 6.1)
    - `KpiCards`: Cost/Impressions/Clicks(원본만), Purchases/Revenue/ROAS(원본+SingleONE), SingleONE 구매 옆 ⓘ("자체 내부 전환 기준입니다."), show/hide를 localStorage(`singleone.dashboard.kpiVisibility`)에 저장(Drag&Drop 없음), 이전 기간 대비 변화율 표시. 내부 필터율 숫자는 어디에도 없음.
    - `MediaIndexChart`(Index ⓘ tooltip, PRD 6.4 문구 그대로), `PerformanceTable`(컬럼 정렬), `IndexBreakdownChart`, `RollingIndexChart` — 전부 Backend가 이미 계산한 값을 그대로 표시만 함(Frontend에 계산식 없음).
    - `JourneySummaryPlaceholder`: 실제 Journey & Attribution 계산 엔진이 아직 없어(사용자 확인) 자리표시자로만 준비.
    - `/dashboard/media/[media]`: 매체 클릭 시 광고주/프로젝트/기간/이전 기간 비교 상태를 query string으로 넘겨 이동하는 준비용 스텁 라우트(실제 매체 상세는 다음 단계).
    - Loading/Error/Empty(광고주 미입력, 프로젝트 없음, 캠페인 없음) 상태 처리.
- 테스트 결과:
  - `SingleOneIndexCalculatorTest`(Docker 불필요, 항상 자동 실행): Golden Dataset 기반으로 새 필드(rawTotals/rawPerformance/components 가중합=indexScore 일관성) 및 `aggregateProjectTotals` 합계 검증 추가, 전부 통과.
  - `DashboardServiceTest`/`analytics.SingleOnePerformanceServiceTest`(Testcontainers)는 이 PC Docker 제약으로 자동 실행 불가 — `bootRun`+curl로 Dashboard API 전체 시나리오(정상 응답 필드 전부 확인, 존재하지 않는 프로젝트 400) 수동 검증 완료.
  - Frontend: `npm test` 15개(신규 6개) 전부 통과, `npm run build`/`npm run lint` 통과. 실제 Backend+Frontend를 띄우고 40일치 성과 데이터로 브라우저에서 골든 패스 전체(프로젝트 자동 선택 → KPI/Index/Breakdown/Rolling 차트 렌더 → 데이터 부족 매체 배지 → 성과 테이블 정렬 → KPI 숨김이 새로고침 후에도 유지 → 매체 클릭 시 컨텍스트가 올바르게 전달되어 스텁 화면 이동)까지 확인.
  - 기존 회귀 테스트(`common.time`, `upload.*`, `migration.clickhouse`, `analytics.SingleOneIndexCalculatorTest`) 전부 재실행해 통과 확인.
- 진행 중 발견해 고친 문제 1건: 필터 영역에서 "이전 기간 비교" 스위치 라벨이 좁은 공간에서 세로로 줄바꿈되는 레이아웃 버그를 스크린샷 확인 중 발견해 `whiteSpace: nowrap`으로 수정함.

## 6. 매체/캠페인/광고 그룹/광고 상세

- 상태: 완료
- 생성물:
  - Backend: `PerformanceAggregationRepository.fetchEntityTotals` 메서드 1개 추가 — 매체(+선택적으로 캠페인/광고그룹/광고까지) 범위의 기간 전체 합계를 (campaign_id, ad_group_id, ad_id) 단위로 반환(기존 `fetchDailyMediaTotals`와 동일한 argMax dedup 패턴 재사용, 하위 레벨은 운영일 개념이 없어 날짜별이 아닌 기간 전체 합산). 이 메서드 하나로 4개 레벨의 "본인 합계"/"하위 목록"을 전부 충당(스코프만 좁혀서 재사용). `AdGroupMasterRepository`/`AdMasterRepository`에 스코프 조회 메서드 각 1개 추가(표시 이름은 기존 Master Upsert의 `latestName` 그대로 사용).
  - 신규 패키지 `com.singleone.backend.detail`: `EntityPerformance`/`EntityPerformanceComparison`/`MediaDetailResponse`(record) + `DetailService`(검증 후 계산 조합 — Index는 Stage 3/5 계산 결과에서 해당 매체 1개만 추림, 원본/SingleONE 성과는 Stage 3 계산 함수 재사용) + `DetailController`(REST 7개, 계층 URL 그대로: `/api/v1/projects/{projectId}/media/{media}/summary`, `.../campaigns`, `.../campaigns/{campaignId}/summary`, `.../ad-groups`, `.../ad-groups/{adGroupId}/summary`, `.../ads`, `.../ads/{adId}/summary`). 하위 목록 3개는 검색/정렬 대상이 ClickHouse 집계 이후 값이라 Java에서 검색·정렬·페이지 슬라이싱 후 `PageImpl`로 반환(기존 clamp 패턴과 동일하게 기본 50/최대 200).
  - `SingleOnePerformanceService.getProjectOrThrow`가 `IllegalArgumentException` 대신 기존 `ProjectRequestException`을 던지도록 정리(전역 `ProjectExceptionHandler`로 일관된 400 응답).
  - Frontend 공용 컴포넌트(`frontend/app/detail/`, 라우트 아님): `Breadcrumb`(단순 렌더링), `PerformanceSummary`(원본 vs SingleONE + ⓘ 툴팁, `previous`/`indexSection` prop으로 계층별 표시 항목만 다르게 — 4개 화면 전부 재사용), `ChildEntityTable`(검색+정렬+페이지네이션을 서버 재조회로 구현하는 범용 목록 — 캠페인/광고그룹/광고 목록 3곳 재사용). `frontend/lib/format.ts`로 숫자/퍼센트 포맷 함수를 뽑아 Dashboard의 기존 `KpiCards`/`PerformanceTable`도 함께 정리(중복 제거).
  - 라우트(Stage 5 스텁을 실제 구현으로 교체): `/dashboard/media/[media]`(매체 상세 — Index+Breakdown+7일 Rolling+캠페인 목록), `.../campaigns/[campaignId]`(캠페인 상세 — 이전 기간 비교+광고그룹 목록, Index 없음), `.../ad-groups/[adGroupId]`(광고그룹 상세 — 광고 목록만, 이전 기간/Index 없음), `.../ads/[adId]`(광고 상세 — 하위 목록 없음). 모든 라우트가 Dashboard에서 넘어온 advertiserId/projectId/from/to/comparePrevious를 query string으로 계속 이어 붙이고, 추가로 캠페인/광고그룹 이름도 하위 화면으로 전달해 Breadcrumb에 ID 대신 이름이 표시되도록 함.
- 해석이 필요했던 부분(계획 승인 시 사용자에게 명시): 계층 상세를 하나의 펼침 테이블이 아니라 별도 route로 분리, Index 점수는 매체에만 계산(Hard Rule 8), 최소 조건(PRD 7.6)은 매체 Index 계산에만 적용되고 캠페인/광고그룹/광고 성과 값 자체는 항상 노출.
- 테스트 결과:
  - Backend: `SingleOneIndexCalculatorTest` 등 기존 회귀 재실행해 통과. 신규 `DetailServiceTest`(Testcontainers, 6개 시나리오)는 이 PC의 Docker Desktop 비호환(1단계부터 동일한 환경 제약)으로 `./gradlew test` 자동 실행 불가 — `bootRun`+curl로 7개 API 전부 수동 검증: 매체 상세 Index가 Dashboard와 동일한 값 재현, 이전 기간 없을 때 `MISSING_REQUIRED_DATA`, 7일 Rolling 정상, 캠페인/광고그룹/광고 목록의 검색·정렬(`cost,desc` 등)·페이지 크기 300→200 clamp 정상, 캠페인 상세 이전 기간 비교(직전 데이터 없으면 0/null), 프로젝트에 없는 매체·캠페인 요청 시 400 오류. `./gradlew test` 전체 실행 시 9개 실패는 전부 동일한 Testcontainers 환경 제약(신규 실패 없음).
  - Frontend: `npm test` 전체 통과(신규 `PerformanceSummary`/`ChildEntityTable`/매체 상세 페이지 테스트 포함, 기존 `page.test.tsx`에 AC-23 회귀 테스트 1개 추가), `npm run build`(타입체크 포함)/`npm run lint` 통과. 실제 Backend+Frontend를 띄우고 브라우저(Playwright 스크립트, 커밋 대상 아님)로 Dashboard→매체 상세→캠페인 상세→광고그룹 상세→광고 상세까지 정방향 진입(계층별 Index/이전 기간/하위 목록 표시 여부가 표에 정의된 대로 정확함)과 Breadcrumb으로 광고 상세→광고그룹→캠페인→매체→Dashboard까지 역방향 복귀, Dashboard 복귀 시 광고주/프로젝트/기간/이전 기간 비교 상태가 유지되는 것까지 전부 확인.
- 진행 중 발견해 고친 문제 2건:
  1. **AC-23 위반(자체 발견)**: Dashboard 화면이 자신의 필터 상태(광고주/프로젝트/기간/이전 기간 비교)를 URL query string에서 읽어오지 않아, 상세 화면에서 "Dashboard" Breadcrumb으로 돌아오면 매번 빈 상태로 초기화되고 있었다. Dashboard의 각 상태를 URL 파라미터로 초기화하도록 수정하고 회귀 테스트 추가. 이 변경으로 정적 프리렌더링 대상이던 `/dashboard` 페이지가 `useSearchParams()`를 써서 빌드가 실패해(Next.js는 Suspense 경계를 요구함) `Suspense`로 감싸 해결.
  2. **Breadcrumb에 ID만 표시되는 문제(자체 발견)**: 광고그룹/광고 상세 화면에서 상위 캠페인/광고그룹 Breadcrumb이 표시 이름이 아니라 원본 ID(`camp-meta`, `ag-1`)를 보여주고 있었다. 상위 화면에서 하위 화면으로 이동할 때 이미 알고 있는 이름을 query string으로 함께 넘기도록 수정(추가 API 호출 없이 해결, 기존 컨텍스트 전달 패턴을 그대로 확장).

## 7. Journey & Attribution

- 상태: 완료
- 생성물:
  - Backend 신규 패키지 `com.singleone.backend.journey`(Stage 3의 "계산과 DB 접근 분리" 패턴 재사용):
    - `JourneyAttributionCalculator`(Spring/DB 비의존 순수 계산): 사용자별 구매 여정을 구성하고(PRD 9.3 — 구매 전 7일 + 직전 구매시점 이후만 유효 터치포인트, 선택 프로젝트 캠페인만 eligible), unique 채널 집합 기준 Linear Attribution과 unordered Channel Pair를 계산하며, 연속 동일 채널을 압축한 Path를 구매수 기준 Top 20으로 추린다.
    - `JourneyEventRepository`: ClickHouse `journey_event`에서 SUCCESS batch만, `event_id` 기준 `argMax` dedup으로 조회(기존 `PerformanceAggregationRepository`와 동일한 dedup 철학).
    - `JourneyAnalysisService`: 프로젝트 검증 후 `[from-7일, to]` 범위만 조회하면 충분함을 수학적으로 확인해 그 범위만 fetch(7일/직전구매 규칙 모두 그보다 과거 데이터가 필요 없음).
    - `JourneyController`: `GET /api/v1/projects/{projectId}/journey?from=&to=` 단일 엔드포인트 — Dashboard 요약과 Journey 화면 3개 탭이 이 응답 하나를 공유(중복 계산 없음).
    - `TimeUtils`에 `startOfDaySeoul`/`endOfDaySeoul` 2개 메서드 추가(Asia/Seoul 날짜 → UTC Instant 경계 변환, 기존 공용 유틸 확장).
  - Frontend: `frontend/lib/period.ts`(Dashboard의 기간 프리셋 로직을 뽑아 Journey 화면과 공유), `frontend/lib/journeyApi.ts`, 신규 라우트 `frontend/app/journey/page.tsx`(필터 바 + MUI Tabs 3개), `SankeyChart.tsx`+`buildSankeyOption.ts`(순수 함수로 분리해 단위 테스트, 기존 `EChart.tsx` 재사용), `TopPathTable.tsx`/`ChannelAttributionTable.tsx`/`ChannelPairTable.tsx`. Dashboard의 `JourneySummaryPlaceholder.tsx`를 실제 데이터를 보여주는 컴포넌트로 교체(같은 Journey API 재사용, "상세 분석으로 이동" 버튼이 광고주/프로젝트/기간을 유지한 채 `/journey`로 이동).
- 해석/설계 확정 사항(계획 승인 시 명시):
  - PRD 9.1 "전체 구매 중 비중"은 매출이 아니라 구매(Journey) 건수 기준 비중이라는 PRD 명시 사항을 그대로 구현(해석이 아님).
  - 유효 터치포인트가 0건인 구매는 배분할 채널이 없어 Attribution/Pair/Path 결과에서 제외(오류 아님, Golden Dataset엔 없는 케이스).
  - Sankey는 같은 경로 안에서 채널이 비연속으로 반복될 수 있어(PRD AC-36 Meta→Google→Meta 예시) 채널명만으로 노드를 만들면 순환 그래프가 되어 렌더링이 깨진다 — 노드 이름에 단계 번호를 붙여(`"1. Meta"`/`"3. Meta"`) 순환을 없앰.
- 테스트 결과:
  - **`JourneyAttributionCalculatorTest`(순수 JUnit, Docker 불필요, 9개 전부 통과)**: PRD 15.4 Golden Journey Dataset을 그대로 재현해 Google 1.833333/Meta 1.333333/TikTok 0.833333(합계 4.000000), Channel Pair Meta+Google=2/Meta+TikTok=2/Google+TikTok=1을 정확히 검증(AC-39/40/41). 추가로 AC-34(7일 경계 포함/8일 제외), AC-35(연속 동일 채널 압축), AC-36(비연속 반복 채널 각 0.5), AC-37(직전 구매 이전 클릭 재사용 금지), AC-38(프로젝트 밖 캠페인 클릭 제외), 유효 터치포인트 0건 케이스까지 전부 통과.
  - `TimeUtilsTest`에 추가한 `startOfDaySeoul`/`endOfDaySeoul` 2개 테스트 통과.
  - `JourneyAnalysisServiceTest`(Testcontainers)는 이 PC Docker 제약(1단계부터 동일)으로 자동 실행 불가 — `bootRun`으로 실제 서버를 띄우고 PRD 15.4 Golden Journey Dataset을 Journey CSV로 직접 업로드한 뒤 `GET /api/v1/projects/{id}/journey`를 curl로 호출해 **API 응답이 PRD 수치와 소수점까지 정확히 일치**함을 확인(Google 1.8333333333333333333333333333333333, Meta 1.3333333333333333333333333333333333, TikTok 0.8333333333333333333333333333333333, Pair 2/2/1, 합계 구매매출 450,000). 존재하지 않는 프로젝트 요청 시 400 오류도 확인. 이 과정에서 실제 버그 2건을 발견해 고침(아래).
  - `./gradlew test` 전체 재실행 결과 48개 중 10개 실패는 전부 기존과 동일한 Testcontainers 환경 제약(신규 실패 없음).
  - Frontend: `npm test` 전체 통과(신규 `buildSankeyOption`/`JourneySummaryPlaceholder`/`/journey` 페이지 테스트 포함), `npm run build`(`/journey` 라우트 정상 등록, 타입체크 포함)/`npm run lint` 통과. 실제 Backend+Frontend를 띄우고 브라우저(Playwright 스크립트, 커밋 대상 아님)로 Dashboard Journey 요약 → "상세 분석으로 이동"(필터 컨텍스트 유지 확인) → `/journey`의 사용자 여정(Sankey+Top Path 테이블)/채널별 전환 기여도(안내 문구, "SingleONE 기여 구매" 미사용 확인)/채널 페어 인사이트(인과적 표현 미사용 확인) 3개 탭까지 전부 실제 렌더링을 스크린샷으로 확인함.
- 진행 중 발견해 고친 버그 2건(전부 수동 curl 검증 중 발견 — Docker 제약으로 Testcontainers를 못 쓰는 상황에서 이 수동 검증이 없었다면 놓쳤을 것들):
  1. `JourneyEventRepository`의 조회 SQL이 WHERE절에서 `event_timestamp`를 필터링하면서 동시에 SELECT에서 같은 이름으로 `argMax(event_timestamp, upload_batch_id) AS event_timestamp`를 만들어, ClickHouse가 WHERE의 참조를 집계 별칭으로 오인해 `ILLEGAL_AGGREGATION` 오류를 냄. → 기존 `PerformanceAggregationRepository.fetchEntityTotals`와 동일하게 내부 쿼리(원본 컬럼으로 필터링)와 바깥 쿼리(집계)를 분리하는 구조로 수정.
  2. 날짜 필터 값을 `java.sql.Timestamp.toString()`으로 문자열화했는데, 이 메서드는 초 단위가 0이어도 항상 `.0`을 붙여(`"2026-06-24 00:00:00.0"`) ClickHouse `DateTime` 타입 파싱이 `Cannot convert string ... to type DateTime` 오류로 실패함. → `DateTimeFormatter`로 UTC "yyyy-MM-dd HH:mm:ss" 문자열을 직접 생성하도록 수정(실제 저장된 데이터로 UTC 그대로 저장됨을 `docker exec`로 재확인).
- 알려진 제약: `JourneyAnalysisServiceTest`는 1~6단계와 동일한 이유(이 PC Docker Desktop/Testcontainers 비호환)로 자동 실행되지 않는다.

## 8. Media Planning Simulation Model 및 UI

- 상태: 완료
- 생성물:
  - Backend 신규 패키지 `com.singleone.backend.simulation`:
    - `WeeklyLogModelFitter`(Spring/DB 비의존 순수 계산): `y = a·ln(x) + b`를 OLS로 적합해 `a/b/R²`와 유효성(a>0 AND R²>=0.50)을 반환. 자연로그·최소제곱 계산에만 한정해 `double`을 쓰고(BigDecimal에 초월함수가 없어 사용자 확인 후 결정한 예외 범위), 그 앞뒤(주간 SingleONE 구매/매출 집계, 기간 환산, CPA/ROAS)는 전부 기존과 동일하게 BigDecimal을 유지.
    - `SimulationService`: 기존 `PerformanceAggregationRepository.fetchDailyMediaTotals`/`SingleOneIndexCalculator.aggregateWindow`/`computeSingleOnePerformance`/`ProjectService.resolveIncludedCampaigns`를 그대로 재사용해 새 집계 코드를 만들지 않고 기준 성과 기간 종료일 기준 최근 8주를 버킷화, 매체마다 유효 주차 판정(cost/impressions/clicks>0, SingleONE 구매>0, 매출>=0) → 유효 주차 수/구매 합계/비용 변동폭 조건(PRD 10.5/AC-47) → 구매·매출 두 모델 적합 → 예산 범위(과거 최소~최대, 150% 외삽) 분류 → 신뢰도(높음/보통/낮음/예측 불가) 산정 → 전체 KPI 산출 가능 여부(AC-52)까지 오케스트레이션. `project.isSystemDefault()`면 즉시 거부(PRD 10.2/AC-22).
    - `SimulationController`: `POST /api/v1/projects/{projectId}/simulation`(순수 record 요청 DTO, 이 코드베이스의 기존 관례대로 Bean Validation 애너테이션 없음).
  - Frontend: `frontend/app/simulation/page.tsx`(광고주/프로젝트 선택 시 시스템 `전체 캠페인` 프로젝트는 목록에서 아예 제외 — Dashboard/Journey처럼 라벨만 붙이는 게 아니라 선택 자체가 안 됨), 기준 성과 기간/시뮬레이션 기간을 `frontend/lib/period.ts`(Stage 7에서 추출한 공용 프리셋)로 각각 따로 입력, 선택된 프로젝트가 포함한 매체마다 예산 입력란 제공(총예산은 자동 합산 표시만, 별도 입력 없음). `frontend/app/simulation/simulationStore.ts`(Zustand, `persist` 미들웨어 미사용 — 새로고침 시 자연 초기화, PRD 10.9/AC-53). `MediaResultTable.tsx`(매체별 카드: 입력/환산현재/예상 구매·매출·CPA·ROAS/신뢰도 배지/관찰 문구), `MarginalEfficiencyChart.tsx`+`buildMarginalEfficiencyOption.ts`(순수 함수로 분리해 단위 테스트, 기존 `EChart.tsx` 재사용해 ECharts `markLine`/`markArea`로 입력 예산·환산 현재 운영·과거 운영 범위·150% 한계를 곡선 위에 표시 — 이 저장소 첫 markLine 사용). 홈 화면에 Journey(Stage 7에서 누락됐던 링크)와 Simulation 링크를 함께 추가.
- 해석/설계 확정 사항(계획 승인 시 사용자에게 명시하고 진행):
  - BigDecimal 예외 범위: OLS 회귀 적합(자연로그, 계수, R²)만 `double`, 그 외 전부 BigDecimal 유지(Hard Rule 13 관련 사용자 확인 완료).
  - 입력 예산이 0원인 매체는 PRD 10.7 표에 신뢰도 등급이 명시돼 있지 않아 신뢰도를 "해당 없음"(null)으로 처리(예상 성과는 0, 전체 KPI는 막지 않음).
  - 신뢰도 "높음" 판정의 R²>=0.75 조건은 구매·매출 두 모델 중 더 낮은 R²(약한 쪽)을 기준으로 판정.
- 테스트 결과:
  - **`WeeklyLogModelFitterTest`(순수 JUnit, Docker 불필요, 6개 전부 통과)**: `y=50·ln(x)-100`으로 정확히 생성한 데이터에서 OLS가 a/b/R²=1을 그대로 복원, 감소형 곡선(a<=0)은 R²가 완벽해도 무효, 노이즈 데이터의 낮은 R²도 무효, 데이터 2개 미만은 무효, 회귀식 적용값 계산과 음수 보정까지 검증.
  - `SimulationServiceTest`(Testcontainers)는 이 PC Docker 제약(1단계부터 동일)으로 자동 실행 불가 — `bootRun`+curl로 실제 8주치 성과 데이터를 업로드해 수동 검증: 충분한 데이터가 있는 매체는 신뢰도 "높음"으로 정상 예측(구매/매출/CPA/ROAS 전부 산출), 데이터가 3주치뿐인 매체는 "데이터 부족" 사유로 예측 불가, 그 매체 예산이 0보다 커 전체 KPI가 산출 불가로 전환되는 것(AC-52)까지 확인. 추가로 예산 0원(예상 성과 0, 신뢰도 없음, 전체 KPI 안 막힘), 과거 최대의 150% 초과(예측 불가), 최대~150% 사이(신뢰도 낮음), 시스템 `전체 캠페인` 프로젝트 선택 거부까지 전부 확인.
  - `./gradlew test` 전체 재실행 결과 55개 중 11개 실패는 전부 기존과 동일한 Testcontainers 환경 제약(신규 실패 없음).
  - Frontend: `npm test` 전체 통과(신규 `buildMarginalEfficiencyOption`/`simulationStore`/`/simulation` 페이지 테스트 포함), `npm run build`(`/simulation` 라우트 정상 등록, 타입체크 포함)/`npm run lint` 통과. 실제 Backend+Frontend를 띄우고 브라우저(Playwright 스크립트, 커밋 대상 아님)로 전체 시나리오 확인: 금지 표현("추천 예산"/"증액 추천"/"감액 추천"/"최적 예산"/"구매 최대화"/"매출 최대화") 미노출, 전체 캠페인 프로젝트가 드롭다운 옵션에서 실제로 빠짐, 예산/기간 입력 후 실행 시 신뢰도 "높음" 매체와 "예측 불가" 매체가 카드로 각각 표시, 한계 효율 곡선에 입력 예산/과거 운영 범위/150% 한계선이 실제로 그려짐, 전체 KPI "산출 불가" 및 면책 문구 노출, **새로고침 후 모든 입력값이 빈 상태로 초기화되는 것(AC-53)까지 스크린샷과 함께 확인**.


## 9. Playwright 핵심 E2E 및 Edge Acceptance Criteria 완료

- 상태: 완료
- 새 기능 추가 단계가 아니라, 1~8단계에서 만든 SingleONE 전체가 PRD AC-01~AC-55를 실제로 만족하는지 전수 재검증한 최종 단계다. 상세 결과는 `docs/FINAL_VALIDATION_REPORT.md`(AC별 표, Backend/Frontend/E2E/Golden Dataset 결과, 잔여 이슈, 실행 방법 포함)에 별도로 기록했다.
- 생성물:
  - Backend 테스트 갭 보강: `DetailServiceTest`에 AC-11(계층 합산 정밀도)/AC-12(하위 소규모 성과 노출) 추가, `JourneyAttributionCalculatorTest`에 AC-55(Top 20 절단) 추가, `SimulationServiceTest`에 AC-44/45/46/49/50 추가, 신규 `analytics/ApiResponseFilterRateNonDisclosureTest.java`(AC-25, 응답 DTO 11종에 filterRate 필드가 없음을 record 컴포넌트 리플렉션으로 검증), 신규 `upload/UploadServiceFileSizeLimitTest.java`(AC-32, Mockito로 50MB 초과 거부 검증).
  - Frontend 테스트 갭 보강: 신규 `lib/period.test.ts`(AC-01), `dashboard/page.test.tsx`에 AC-01/05/06/08/09 추가, `journey/page.test.tsx`에 AC-42 추가, `simulation/page.test.tsx`에 AC-43(결과 렌더 후 재검사)/AC-50/AC-52/AC-53 추가, `uploads/page.test.tsx`에 AC-29 추가, `detail/ChildEntityTable.test.tsx`에 AC-54(200 최대 옵션) 추가.
  - Playwright E2E 신설(그동안 throwaway 스크립트로만 확인하던 것을 커밋 대상 자산으로 전환): `frontend/e2e/dashboard-and-detail.spec.ts`, `upload-lifecycle.spec.ts`, `journey.spec.ts`, `simulation.spec.ts` + 공용 시딩 헬퍼 `frontend/e2e/testData.ts`.
  - Testcontainers 재시도: `DOCKER_HOST`를 여러 파이프로 재지정해 재확인했으나 1단계부터 동일한 `IllegalStateException`으로 실패 — 근본 원인이 여전히 동일함을 최종 확인(코드 결함 아님, 환경 제약 그대로 유지).
- 이번 단계에서 실제로 발견해 고친 결함 2건(테스트를 새로 작성하는 과정에서 발견 — 이 검증이 없었다면 놓쳤을 것들):
  1. **`frontend/lib/period.ts` 타임존 버그**: `toISODate`가 `Date.toISOString()`(UTC)을 쓰는데 `computeRange`의 "이번 달"/"지난 달"은 로컬 시간 기준(`new Date(year, month, 1)`)으로 계산해, UTC+9(Asia/Seoul)에서 "이번 달"/"지난 달" 기간이 실제로 하루씩 밀리는 버그가 있었다. `toISODate`를 로컬 연/월/일 getter 기반으로 재작성해 수정(Hard Rule 14 준수).
  2. **PRD 10.3 Tooltip 누락**: Simulation 화면 "환산 현재 운영"에 PRD가 명시한 안내 문구가 없었다. `MediaResultTable.tsx`에 기존 ⓘ Tooltip 패턴 그대로 추가.
- 테스트 결과: Backend `./gradlew test` 59개 중 48개 자동 통과, 11개는 기존과 동일한 Testcontainers 환경 제약(전부 `bootRun`+curl로 fresh 재검증 통과). Frontend `npm test` 61개 중 60개 통과(1개는 반복 확인된 부하성 flake, 단독 실행 시 항상 통과), `npm run build`/`npm run lint` 통과. Playwright `npx playwright test --workers=1` 6/6 전부 통과. Golden Index Dataset과 Golden Journey Dataset 모두 기대값 변경 없이 그대로 통과. AC-01~AC-55 전부 PASS(자세한 근거는 `docs/FINAL_VALIDATION_REPORT.md` 표 참고).

## 10. UI/UX 디자인 개선 (기능/로직 변경 없음)

- 상태: 완료
- 새 기능 추가 단계가 아니라, 1~9단계에서 이미 완성된 SingleONE의 화면 디자인만 B2B SaaS 대시보드 수준으로 다듬은 단계다. API/계산 로직/데이터 모델/route 구조는 전혀 바꾸지 않았다(사용자가 명시한 절대 원칙).
- 생성물:
  - 디자인 시스템: `frontend/app/theme.ts`(브랜드 블루 팔레트, 타이포 스케일, Card/Button/Chip/Table/Tab 등 MUI 컴포넌트 공통 스타일), `frontend/app/globals.css`(Arial 기본값 제거), `frontend/app/layout.tsx`(Noto Sans KR 웹폰트 `next/font`로 self-host).
  - 공통 레이아웃: 신규 `frontend/app/AppShell.tsx` — 모든 화면을 감싸는 상단 바(로고+제품명)와 좌측 아이콘 사이드바(홈/데이터 관리/프로젝트/대시보드/Journey/Simulation). 기존 route는 그대로 두고 이동 수단만 추가함.
  - 매체 색상 통일: 신규 `frontend/lib/mediaColors.ts` — META/GOOGLE/TIKTOK/NAVER/CRITEO 고정 색상표. `MediaIndexChart`/`RollingIndexChart`/`PerformanceTable`/`buildSankeyOption`(Journey Sankey)/`MediaResultTable`/`buildMarginalEfficiencyOption`(Simulation 곡선)이 전부 이 표를 공유해, 같은 매체는 모든 차트에서 같은 색으로 보인다.
  - 공통 재사용 컴포넌트(신규 `frontend/app/components/common/`): `PageHeader`(페이지 타이틀), `FilterPanel`(광고주ID/프로젝트/기간 등 필터 영역 공통 컨테이너), `SectionCard`(제목+ⓘ툴팁+본문을 감싸는 카드, 기존에 반복되던 `<Paper><Typography variant="h6">...` 패턴 대체), `StatCard`(KPI/지표 표시 공통화). Dashboard/Journey/Simulation/Projects/Uploads/매체·캠페인·광고그룹·광고 상세 화면 전부 이 컴포넌트들로 교체했고, `KpiCards`와 `PerformanceSummary`의 지표 카드 로직을 `StatCard` 하나로 통합했다.
  - 실제 레이아웃 버그 수정(디자인 개선 중 발견): Dashboard KPI 카드 6개가 그리드에 안 맞아 마지막 카드가 혼자 다음 줄로 밀리던 문제, Simulation 매체별 예산 입력 필드 라벨이 서로 겹치던 문제, 한계효율 차트의 주석 라벨 3개가 서로 겹치던 문제(ECharts markLine이 기본적으로 라벨을 세로로 회전시켜 글자가 겹쳐 보이는 문제였음 — `rotate: 0`과 위치 분산으로 해결), Projects 화면 "새 프로젝트" 버튼 텍스트가 좁은 공간에서 잘리던 문제.
- 해석/설계 확정 사항(계획 승인 시 사용자에게 명시):
  - 사이드바 메뉴 라벨은 "대시보드"(한글)로 표기해 상세 화면 Breadcrumb의 "Dashboard"(영문, PRD 화면명 그대로) 링크와 접근성 이름이 겹치지 않게 함(둘 다 "Dashboard"였다면 자동화 도구/스크린리더가 어떤 링크인지 구분 못하는 문제가 실제로 있었음).
  - `reference-example-dashboard.png` 참고 이미지의 "예산 추천 시뮬레이션"/"추천 예산 적용"/"시나리오 저장" 패널은 카드/표의 시각적 스타일만 참고하고 추천·자동적용·저장 기능은 차용하지 않음(Hard Rule 5/6).
- 테스트 결과:
  - Frontend: `npm test`(Vitest, `--no-file-parallelism`으로 직렬 실행) 61개 전부 통과. `npm run build`(타입체크 포함) 성공.
  - Playwright `npx playwright test --workers=1` 6/6 전부 통과(1회는 전체 동시 실행, 나머지는 자원 경합으로 개별 재실행 후 통과 — 이 PC의 메모리 여유가 낮을 때(사용자 Chrome이 다량 사용 중) 발생하는 이미 알려진 환경적 지연이며 코드 결함 아님).
  - 실제 Backend+Frontend+데모 데이터(`abc-brand`)로 Dashboard/Journey/Simulation/Projects/Uploads/매체 상세 화면을 브라우저 스크린샷으로 직접 확인.
  - 기능 변경 자체 점검: Frontend 코드 diff는 스타일/마크업/공통 컴포넌트 추출만 포함하며, API 호출 경로·요청 파라미터·상태 관리 로직·계산 로직은 모두 기존 그대로임을 확인.
- 진행 중 발견해 고친 버그 2건(디자인 작업 중 실제로 발견):
  1. AppShell 로고 옆 "SingleONE" 글자가 MUI `subtitle1`의 기본 HTML 태그(`<h6>`)로 렌더링되어, 홈 화면의 실제 제목(`<h1>SingleONE</h1>`)과 이름이 같은 heading이 2개가 되는 문제 — `component="span"`으로 수정.
  2. 위 문제의 연장선으로, 사이드바의 "Dashboard" 메뉴와 상세 화면 Breadcrumb의 "Dashboard" 링크 이름이 같아 자동 테스트(및 접근성 도구)가 어떤 링크인지 구분하지 못하던 문제 — 사이드바 라벨을 "대시보드"로 변경해 해결.

## 11. 전역 광고주 선택 + Dashboard 정보 구조 최적화

- 상태: 완료
- 기존 기능 추가가 아니라, 화면마다 따로 관리하던 "광고주 ID" 입력을 상단 Global Header 하나로 통합하고, Dashboard의 정보 밀도를 대형 모니터 시연에 맞게 재구성한 단계다. SingleONE 계산 로직·API 계약·DB 구조·Journey/Simulation 계산식은 전혀 바꾸지 않았다.
- 계획 승인 전 두 가지 지점에서 PRD/기존 기능과의 충돌을 확인하고 사용자에게 직접 확인함:
  1. PRD 4.1(메인 내비게이션)과 6.3/9.1~9.2는 Journey & Attribution을 별도 메인 메뉴/독립 화면으로 명시한다. 사용자가 애초에 요청한 "Dashboard에 완전 통합"은 이 PRD 내용과 상충해, **메인 메뉴/독립 `/journey` 페이지는 그대로 유지**하고 Dashboard의 기존 "Journey & Attribution 요약" 위젯만 레이아웃상 더 눈에 띄게(매체별 Index 차트와 좌우 배치) 재배치하는 것으로 범위를 조정하기로 확인받음. `/journey` route·메인 메뉴 항목·Journey 계산 로직은 전혀 손대지 않았다.
  2. 데이터 관리(업로드) 화면은 신규 광고주가 시스템에 처음 등록되는 유일한 경로(업로드 성공 시 Advertiser Master Upsert로 자동 생성)라, 전역 드롭다운(기존 데이터가 있는 광고주만 표시)만 남기면 UI로 신규 광고주를 등록할 방법이 없어진다는 점을 확인받아, **데이터 관리 화면만 예외로 자체 광고주 ID 입력을 그대로 유지**하기로 확정.
- 생성물:
  - Backend: 신규 `com.singleone.backend.advertiser` 패키지 — `AdvertiserController`(`GET /api/v1/advertisers`, 기존 `AdvertiserRepository.findAll()`만 재사용하는 최소 컨트롤러, 별도 Service/Exception 불필요), `AdvertiserResponse` record. 성과 업로드 SUCCESS 시 Master Upsert가 이미 Advertiser 행을 항상 만들어두므로(Stage 1/2) 이 엔드포인트가 곧 "실제 데이터가 있는 광고주 목록"이다.
  - Frontend 전역 상태: 신규 `frontend/lib/advertiserApi.ts`, `frontend/lib/advertiserStore.ts`(Zustand, `simulationStore.ts`와 동일하게 persist 미들웨어 없음 — 목록 로드 후 선택값이 없거나 더 이상 존재하지 않으면 첫 번째 광고주를 자동 선택), 신규 `frontend/app/components/common/AdvertiserSelector.tsx`(MUI Autocomplete, Loading/Empty/Error 처리)를 `AppShell.tsx` 상단 바 우측에 배치. 상세 화면(매체/캠페인/광고그룹/광고) 체류 중 광고주가 바뀌면 해당 데이터가 새 광고주에 존재하지 않을 게 확실하므로 Dashboard로 자동 이동시키는 효과를 `AppShell`에 추가.
  - Frontend 페이지별 정리: `dashboard/page.tsx`, `journey/page.tsx`, `projects/page.tsx`, `simulation/page.tsx`(+`simulationStore.ts`에서 `advertiserId` 필드 제거)의 자체 "광고주 ID" TextField와 로컬/URL 기반 상태를 제거하고 전역 store를 참조하도록 교체. 기존에 이미 있던 "광고주 변경 시 프로젝트 재조회 + 무효 선택 해제"(PRD 6.1) `useEffect`는 의존값 출처만 바뀌었을 뿐 로직은 그대로 재사용. 매체/캠페인/광고그룹/광고 상세 4개 페이지의 `carryOverQuery()`에서도 이제 불필요해진 `advertiserId` 파라미터를 제거.
  - Dashboard 레이아웃 최적화(대형 모니터): `Container maxWidth`를 `lg`→`xl`로 확대. "매체별 SingleONE Index" 차트와 "Journey & Attribution 요약"(기존 `JourneySummaryPlaceholder`, 내용/계산 변경 없음)을 2열 그리드로 좌우 배치, "SingleONE Index 구성요소 Breakdown"과 "7일 Rolling SingleONE Index"도 2열 그리드로 좌우 배치. 1920×1080 기준 스크린샷으로 확인한 결과 이전보다 스크롤이 크게 줄었다(표+두 차트 Row까지 한 화면에 거의 다 들어옴).
- 테스트 결과:
  - Backend: 신규 `AdvertiserControllerTest`(Mockito 기반, Docker 불필요) 포함 `./gradlew test` 61개 중 50개 자동 통과, 11개는 1단계부터 동일하게 기록된 Testcontainers/Docker Desktop 환경 제약(신규 실패 없음, 기존 문서화된 것과 정확히 같은 11개 테스트). `bootRun` + curl로 `GET /api/v1/advertisers` 실제 응답 확인.
  - Frontend: 기존 `dashboard/page.test.tsx`/`journey/page.test.tsx`/`projects/page.test.tsx`/`simulation/page.test.tsx`/`simulationStore.test.ts`/`dashboard/media/[media]/page.test.tsx`의 광고주 텍스트박스 기반 테스트를 전역 store를 직접 `setState`하는 방식으로 교체(기존 `simulationStore`를 테스트에서 직접 `setState`하던 패턴 재사용). 신규 `lib/advertiserStore.test.ts`, `app/components/common/AdvertiserSelector.test.tsx` 추가. `npm test`(`--no-file-parallelism`) 70개 전부 통과(1회는 자원 경합으로 인한 재현 가능한 flake, 단독 실행 시 항상 통과 — 이미 여러 단계에서 반복 확인된 이 PC의 환경적 지연이며 코드 결함 아님). `npm run build`(타입체크 포함) 성공.
  - Playwright E2E: `dashboard-and-detail.spec.ts`/`journey.spec.ts`/`simulation.spec.ts`의 광고주 텍스트박스 입력 단계를 신규 헬퍼 `selectAdvertiser()`(헤더 Autocomplete를 열고 방금 만든 advertiserId로 필터해 옵션 클릭)로 교체. `upload-lifecycle.spec.ts`는 데이터 관리 화면 자체 입력을 그대로 쓰므로 변경 없음. `npx playwright test --workers=1` 실행 결과는 아래에 기록.
  - 실제 Backend+Frontend+데모 데이터(`abc-brand`)로 광고주를 바꾸면 Dashboard/프로젝트/Simulation이 즉시 새 데이터로 갱신되는 것, 매체 상세 화면 체류 중 광고주를 바꾸면 Dashboard로 자동 이동하는 것, 데이터 관리 화면에는 광고주 ID 입력이 그대로 남아있는 것을 브라우저로 직접 확인.
- 진행 중 발견해 고친 점: 없음(디자인 개선 단계인 Stage 10에서 이미 정리된 공통 컴포넌트/테마를 그대로 재사용했고, 새로 발견된 버그는 없었다).

---

## 12. Docker Compose 전체 스택 One-shot 구성

- 상태: 완료
- 새 기능이 아니라, 지금까지 Docker(MySQL/ClickHouse)만 컴포즈하고 Backend/Frontend는 `gradlew bootRun`/`npm run dev`로 따로 켜던 방식에 더해, 명령 한 줄(`docker compose up -d --build`)로 전체 스택을 띄울 수 있는 경로를 추가한 단계다. 기존 `infra/docker-compose.yml`과 수동 실행 워크플로우는 그대로 유지된다(hot reload가 필요한 개발 시 계속 사용 가능).
- 생성물:
  - `backend/Dockerfile`(멀티스테이지: `eclipse-temurin:17-jdk-jammy`로 `./gradlew bootJar -x test` 빌드 → `eclipse-temurin:17-jre-jammy` 런타임, healthcheck용 `curl`만 추가 설치), `backend/.dockerignore`.
  - `frontend/Dockerfile`(멀티스테이지: `node:20-slim`으로 `npm ci && npm run build` → 프로덕션 의존성만 재설치한 `node:20-slim` 런타임에서 `npm run start`), `frontend/.dockerignore`. `NEXT_PUBLIC_API_BASE_URL`은 브라우저가 Backend를 직접 호출하는 구조라 빌드 인자로 받아 `next build` 시점에 그대로 번들에 포함시킨다.
  - 루트 `docker-compose.yml`: Docker Compose `include:`로 기존 `infra/docker-compose.yml`(mysql/clickhouse)을 그대로 재사용하고, `backend`/`frontend` 서비스를 추가(설정 중복 없이 한 파일에서 합성). `backend`는 `MYSQL_HOST`/`CLICKHOUSE_HOST`를 컨테이너 네트워크 서비스명(`mysql`/`clickhouse`)으로 고정 오버라이드(`.env`의 `localhost` 값은 호스트에서 직접 실행할 때만 유효하므로), `depends_on: condition: service_healthy`로 mysql/clickhouse → backend → frontend 순으로 기동 순서를 강제.
- 테스트 결과: 실제로 Docker Desktop을 켠 상태에서 `docker compose --env-file .env up -d --build`를 실행해 4개 컨테이너(mysql/clickhouse/backend/frontend) 전부 `healthy`/`Up`까지 확인. `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`, `curl -o /dev/null -w '%{http_code}' http://localhost:3000` → `200`, `GET /api/v1/advertisers` 응답 확인까지 실제로 검증했다. 이 스택은 `infra/` 방식과 별도의 Docker Volume을 쓰므로(Compose 프로젝트명이 실행 폴더 기준으로 다름) 데이터가 서로 공유되지 않는다는 점을 README에 명시했다.
- 진행 중 발견해 고친 점: 버그는 아니고 로컬 환경 문제 — 이전에 수동으로 켜둔 `gradlew bootRun`/`npm run dev` 프로세스가 각각 8080/3000 포트를 점유하고 있어 `docker compose up`이 "port is not available" 오류로 backend/frontend 컨테이너를 못 띄운 사례가 있었다. 해당 프로세스를 종료(사용자 확인 후 진행)하니 정상적으로 기동했다 — README 5번 오류 표에 원인/해결법을 추가했다.

---

## Acceptance Criteria 커버리지 (AC-01 ~ AC-55)

9단계(최종 Acceptance Test)에서 AC-01~AC-55 전체를 전수 재검증했다. **55개 전부 완료(PASS)**. AC별 검증 대상/테스트 방법/자동 테스트 파일/실제 결과/비고는 `docs/FINAL_VALIDATION_REPORT.md`에 상세히 기록돼 있다.

| AC 범위 | 관련 단계 | 상태 |
|---|---|---|
| AC-01, AC-14~AC-16 | Dashboard | 완료 |
| AC-02~AC-13 | Index 계산 | 완료 |
| AC-17~AC-22 | Project | 완료 |
| AC-23~AC-24 | 상세 화면 / Breadcrumb | 완료 |
| AC-25 | 필터율 비공개 | 완료 |
| AC-26~AC-33 | 업로드 | 완료 |
| AC-34~AC-42 | Journey & Attribution | 완료 |
| AC-43~AC-52 | Media Planning Simulation | 완료 |
| AC-53 | Simulation 비저장 | 완료 |
| AC-54 | Pagination | 완료 |
| AC-55 | Journey Top 20 | 완료 |
