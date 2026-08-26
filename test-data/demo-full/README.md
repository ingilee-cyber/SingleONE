# SingleONE 종합 테스트 데이터 세트 (demo-full)

SingleONE의 이미 구현된 기능(Dashboard/SingleONE Index, Project, Media Planning Simulation,
Journey & Attribution, 업로드 오류 처리, 전역 광고주 전환)을 결정론적으로 재현 가능한 데이터로
검증/시연하기 위한 데이터셋이다. **제품 코드(계산식/API/DB 스키마)는 변경하지 않았다** — 아래
"알려진 이슈" 항목은 이번에 발견했지만 수정하지 않은 기존 Backend 버그다.

빠른 스모크 테스트가 필요하면 기존 `test-data/demo_performance.csv` / `test-data/demo_journey.csv`
(단일 광고주 `abc-brand`, 소규모)를 그대로 사용하면 된다. 이 문서가 다루는 `demo-full/`은 그보다
훨씬 큰, 3개 광고주 종합 데이터셋이다.

## 한 번에 준비하기

```bash
# 1) 데이터 생성 (test-data/generator/에서 실행, 최초 1회 npm install 필요)
cd test-data/generator
npm install
npm run generate

# 2) 생성물 검증 (~20개 불변식 자동 체크, 실패 시 exit code 1)
npm run validate

# 3) Backend가 http://localhost:8080에서 실행 중인 상태에서 실제 업로드/Project 생성
npm run seed
```

`frontend/package.json`에도 동일한 스크립트를 passthrough로 등록해 두었다(`npm run
generate:demo-data` / `validate:demo-data` / `seed:demo-data`, frontend 디렉터리에서 실행).

`BACKEND_URL` 환경변수로 Backend 주소를 바꿀 수 있다(기본값 `http://localhost:8080`).

모든 생성은 고정 seed(20260826) 기반이라 몇 번을 다시 생성해도 완전히 동일한 파일이 나온다
(재현성 검증 완료).

## 디렉터리 구조

```
demo-full/
├─ README.md, MANIFEST.json, EXPECTED_BEHAVIOR.md
├─ performance/performance_{advertiser}.csv   (광고주별 Performance, 총 30,864행)
├─ journey/journey_{advertiser}.csv           (광고주별 Journey, 총 16,683건)
├─ projects/projects.json                     (광고주×Project 정의, 기존 Project API로 생성)
├─ xlsx/                                       (XLSX 업로드 픽스처 3종)
└─ invalid-upload/                             (오류 케이스 픽스처 11종 — 정상 Seed 시 업로드 안 됨)
```

`test-data/generator/`(생성기 자체)의 상세 구조는 해당 디렉터리를 참고. XLSX 작성에는
`write-excel-file`(0 vulnerabilities로 확인 후 채택, `xlsx`/`exceljs` 대비 보안 이슈 없음)을
devDependency로만 추가했고, Frontend/Backend 어디에도 영향 없다.

## 광고주 3곳 요약

자세한 수치와 화면별 기대 동작은 [EXPECTED_BEHAVIOR.md](./EXPECTED_BEHAVIOR.md), 정확한 파일
목록/집계는 [MANIFEST.json](./MANIFEST.json)을 참고.

* **aurora-beauty(오로라뷰티)** — 대표 시연용. `상시` 프로젝트는 5개 매체 전부 SingleONE Index
  정상, Simulation도 3개 매체(META/TIKTOK/GOOGLE) HIGH confidence 예측 가능.
* **urban-fit(어반핏)** — Google/Meta 강세, Edge Case 없는 평범한 기준점.
* **living-lab(리빙랩)** — SingleONE Index 데이터 부족 Edge Case(비용 미달/구매 미달/운영일
  미달/구매 0)를 프로젝트별로 의도적으로 배치.

## 데이터 해석 시 주의사항

Journey Event 기반 구매와 Dashboard SingleONE 성과는 서로 다른 분석 데이터이므로 집계값이
동일하지 않을 수 있습니다. 두 값을 강제로 일치시키지 않았다(PRD 규칙 9번 — 두 데이터 풀을
합치지 않음).

## Invalid Upload Fixture 사용법

`invalid-upload/`의 11개 파일은 `seed.mjs`가 자동으로 업로드하지 않는다. 각 오류 화면을 확인하려면
Frontend 업로드 화면(또는 `curl -F` 등)으로 원하는 파일을 직접 업로드하면 된다. 각 파일이 어떤
오류를 재현하는지는 [EXPECTED_BEHAVIOR.md](./EXPECTED_BEHAVIOR.md)의 표를 참고 — 실제 Backend에
업로드해 오류 코드를 확인했다. `performance_duplicate_existing_data.csv`는 `demo-full` 데이터를
먼저 Seed한 뒤 업로드해야 `DUPLICATE_CONFIRMATION_REQUIRED`가 재현된다(Confirm/Cancel 응답 API는
`POST /api/v1/uploads/{batchId}/confirm-overwrite`, `POST /api/v1/uploads/{batchId}/cancel`).

`journey_duplicate_order_id.csv`는 별도의 알려진 이슈가 있다 — EXPECTED_BEHAVIOR.md 참고.

## 대용량(Stress) 테스트

50MB/1,000,000행 업로드 제한 검증용 대용량 파일은 기본 생성에 포함하지 않았고, 이번 버전의
`generate.mjs`에는 `--stress` 옵션도 아직 구현하지 않았다(필요 시 후속 작업으로 추가 가능 —
`test-data/demo-full/stress/`는 `.gitignore`에 이미 등록해 두었다).

## Validation

`npm run validate`(`test-data/generator/validate.mjs`)는 advertiser 수, 운영기간, Project 정의,
ID 유일성 범위, Natural Key 중복, 음수/역전 지표, Journey 7일 경계·다중구매·멀티채널·반복채널 존재
여부, Simulation용 8주 데이터, Index 데이터 부족 Case 존재, 대표 광고주 5개 매체 정상 계산 조건 등
총 63건의 불변식을 검사한다(현재 전부 PASS). 실패 시 이 데이터셋을 완료로 보고하지 않는다.
