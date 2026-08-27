# SingleONE 테스트 제품 실행 가이드

이 문서는 개발 지식이 없어도 SingleONE을 직접 켜고, 데이터를 넣어보고, 화면을 확인할 수 있도록 만든 안내서입니다. 제품 요구사항이 궁금하면 [`docs/SingleONE_PRD.md`](docs/SingleONE_PRD.md)를, 각 단계별 구현/검증 기록이 궁금하면 [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md)와 [`docs/FINAL_VALIDATION_REPORT.md`](docs/FINAL_VALIDATION_REPORT.md)를 참고하세요.

이 가이드는 실제로 컴퓨터를 완전히 초기 상태(빈 데이터베이스)로 되돌린 뒤 처음부터 끝까지 직접 실행해보고 확인한 내용을 바탕으로 작성했습니다.

---

## 0. 미리 준비해야 하는 프로그램

아래 프로그램들이 이미 컴퓨터에 설치돼 있어야 합니다(설치 자체는 이 가이드 범위 밖입니다).

- **Docker Desktop** (Windows용)
- **Java 17** (Eclipse Temurin 등)
- **Node.js 20 이상**

---

## 1. 프로그램을 켜는 순서

### 방법 A. Docker Compose로 한 번에 실행 (추천)

Docker Desktop만 켜져 있으면, MySQL/ClickHouse/Backend/Frontend를 명령어 한 줄로 전부 띄울 수 있습니다.

```powershell
cd "C:\Users\ingi.lee\Desktop\A1mediagroup\회사 자료\9996. 해커톤\SingleONE"
docker compose --env-file .env up -d --build
docker compose ps
```

4개 컨테이너(`singleone-mysql`, `singleone-clickhouse`, `singleone-backend`, `singleone-frontend`)가 모두 `Up`(mysql/clickhouse/backend는 `healthy`까지) 상태면 성공입니다. **http://localhost:3000** 으로 접속하세요.

> `.env` 파일이 없다는 오류가 나면, 같은 폴더의 `.env.example` 파일을 복사해 `.env`로 저장하세요.
> 이미 8080/3000 포트를 다른 프로세스(직접 켜둔 `gradlew bootRun`/`npm run dev` 등)가 쓰고 있으면 "port is not available" 오류가 납니다 — 아래 5번 오류 표를 참고해 해당 프로세스를 먼저 종료하세요.
> 코드를 수정하며 즉시 반영(hot reload)되는 개발 환경이 필요하면 아래 "방법 B"를 대신 쓰세요. 이 방법은 이미지 빌드본을 그대로 실행하는 방식이라 코드를 고쳐도 재빌드(`--build`) 전까지는 반영되지 않습니다.

끌 때는 아래처럼 하면 됩니다(데이터는 유지됨).
```powershell
docker compose stop
```
데이터까지 완전히 지우고 싶으면 `docker compose down -v`.

### 방법 B. 각 프로그램을 직접 켜기 (개발용, hot reload)

터미널(PowerShell) 창을 **3개** 열어서 아래 순서대로 진행하세요. 각 창은 실행한 뒤 닫지 말고 그대로 켜두어야 합니다.

### 1-0. Docker Desktop 실행 확인

작업 표시줄에 고래 모양 Docker 아이콘이 없다면, 먼저 **Docker Desktop 앱을 실행**하고 아이콘이 안정적으로 뜰 때까지(보통 30초~1분) 기다리세요. 이 단계를 건너뛰면 다음 명령어들이 "Docker daemon을 찾을 수 없다"는 오류를 냅니다.

### 1-1. 터미널 1 — MySQL/ClickHouse (데이터베이스) 실행

```powershell
cd "C:\Users\ingi.lee\Desktop\A1mediagroup\회사 자료\9996. 해커톤\SingleONE\infra"
docker compose --env-file ../.env -f docker-compose.yml up -d
docker ps
```

`docker ps` 결과에 `singleone-mysql`, `singleone-clickhouse` 두 개가 모두 `healthy`로 나오면 성공입니다(`health: starting`이면 몇 초 더 기다렸다가 `docker ps`를 다시 입력해보세요).

> `.env` 파일이 없다는 오류가 나면, 같은 폴더의 `.env.example` 파일을 복사해 `.env`라는 이름으로 저장하세요.

### 1-2. 터미널 2 — Backend 실행

```powershell
cd "C:\Users\ingi.lee\Desktop\A1mediagroup\회사 자료\9996. 해커톤\SingleONE\backend"
./gradlew bootRun
```

화면에 `Started BackendApplication...`이라는 줄이 뜨면 성공입니다. 이후로 이 창에 새 글자가 안 올라와도 정상입니다(서버가 계속 켜져 있는 것이 원래 동작입니다) — 화면이 "멈춘 것처럼" 보여도 문제가 아닙니다.

처음 실행할 때는 데이터베이스 테이블을 자동으로 만드는 과정(Migration)이 함께 진행됩니다. 별도로 손댈 것은 없고, 그냥 기다리면 됩니다.

### 1-3. 터미널 3 — Frontend 실행

```powershell
cd "C:\Users\ingi.lee\Desktop\A1mediagroup\회사 자료\9996. 해커톤\SingleONE\frontend"
npm.cmd run dev
```

`Local: http://localhost:3000` 문구가 뜨면 성공입니다.

### 1-4. 접속

웹 브라우저(Chrome 등)에서 아래 주소로 들어가세요.

**http://localhost:3000**

---

## 2. 접속 주소 모음

| 화면 | 주소 |
|---|---|
| 홈(연결 확인용) | http://localhost:3000/ |
| 데이터 관리(업로드) | http://localhost:3000/uploads |
| 프로젝트 | http://localhost:3000/projects |
| Dashboard | http://localhost:3000/dashboard |
| Journey & Attribution | http://localhost:3000/journey |
| Media Planning Simulation | http://localhost:3000/simulation |

매체/캠페인/광고그룹/광고 상세 화면은 Dashboard에서 매체를 클릭하면 자동으로 이동합니다(직접 주소를 입력할 필요 없음).

---

## 3. 프로그램을 끄는 순서

**방법 A(Docker Compose)로 켰다면** 저장소 루트에서 `docker compose stop`(컨테이너만 정지, 데이터 유지) 또는 `docker compose down`(컨테이너 삭제, 데이터는 볼륨에 유지)이면 됩니다.

**방법 B(직접 실행)로 켰다면** 켤 때의 **반대 순서**로 끕니다.

1. 터미널 3(Frontend) 창에서 `Ctrl + C`를 눌러 종료합니다.
2. 터미널 2(Backend) 창에서 `Ctrl + C`를 눌러 종료합니다.
3. Docker(데이터베이스)까지 끄고 싶다면(평소에는 계속 켜둬도 괜찮습니다):
   ```powershell
   cd "C:\Users\ingi.lee\Desktop\A1mediagroup\회사 자료\9996. 해커톤\SingleONE\infra"
   docker compose --env-file ../.env -f docker-compose.yml stop
   ```
   (`stop`은 데이터를 지우지 않고 컨테이너만 멈춥니다. 다음에 켤 때는 `docker compose ... up -d`만 다시 실행하면 됩니다.)

> 두 방법의 MySQL/ClickHouse는 서로 다른 데이터 볼륨을 씁니다(Docker Compose는 실행한 폴더 이름으로 프로젝트를 구분합니다). 방법 A와 방법 B를 번갈아 쓰면 넣어둔 데이터가 안 보일 수 있으니, 한 번에 하나의 방법만 쓰는 것을 권장합니다.

---

## 4. 테스트 데이터 넣는 방법

### 방법 A. 미리 만들어둔 데모 데이터 사용 (추천)

`test-data` 폴더에 바로 사용할 수 있는 데모 데이터가 준비돼 있습니다.

- `test-data/demo_performance.csv` — 5개 매체(Meta/TikTok/Google/Naver/Criteo)의 60일치 성과 데이터
- `test-data/demo_journey.csv` — 사용자 클릭→구매 여정 샘플 데이터

**넣는 순서:**

1. http://localhost:3000/uploads 접속
2. 광고주 ID에 `abc-brand` 입력
3. "성과(Performance) 업로드"에서 `test-data/demo_performance.csv` 선택 → 업로드 → 목록에 "성공" 표시 확인
4. "Journey 이벤트 업로드"에서 `test-data/demo_journey.csv` 선택 → 업로드 → "성공" 표시 확인
5. http://localhost:3000/projects 접속 → 광고주 ID `abc-brand` → "새 프로젝트" → 5개 매체(META/TIKTOK/GOOGLE/NAVER/CRITEO)의 캠페인을 모두 체크 → 저장
6. http://localhost:3000/dashboard 접속 → 광고주 ID `abc-brand` → 방금 만든 프로젝트 선택 → 기간을 "직접 설정"으로 바꾸고, CSV 파일을 만든 날짜 기준 최근 60일 범위를 입력(예: 오늘이 2026-08-25라면 시작일 2026-06-27 / 종료일 2026-08-25)

> `demo_performance.csv`는 **파일을 만든 시점 기준 최근 60일**의 날짜로 만들어져 있습니다. 시간이 많이 지난 뒤 이 파일을 다시 쓰면 "최근 30일" 기본 기간에는 안 잡힐 수 있으니, Dashboard에서 "직접 설정"으로 날짜를 맞춰 확인하세요.

### 방법 B. 직접 만든 CSV 파일 사용

`/uploads` 화면에서 광고주 ID를 입력하고 직접 준비한 CSV(또는 XLSX) 파일을 업로드하면 됩니다. 파일에 꼭 들어가야 하는 열(컬럼) 이름은 `test-data/demo_performance.csv`(성과 데이터)와 `test-data/demo_journey.csv`(Journey 데이터)의 첫 줄을 열어보면 확인할 수 있습니다.

### 방법 C. 종합 데모 데이터셋(광고주 3개, 시연용)

여러 매체/Edge Case를 골고루 보여주는 광고주 3개짜리 데이터셋이 `test-data/demo-full/`에 준비돼 있습니다. Backend가 켜진 상태에서:

```powershell
cd "C:\Users\ingi.lee\Desktop\A1mediagroup\회사 자료\9996. 해커톤\SingleONE\test-data\generator"
npm install
npm run seed
```

자세한 내용은 `test-data/demo-full/README.md`와 `EXPECTED_BEHAVIOR.md`를 참고하세요.

---

## 5. 자주 발생할 수 있는 오류

| 증상 | 원인 | 해결 방법 |
|---|---|---|
| `docker compose` 실행 시 "Docker daemon을 찾을 수 없다"는 오류 | Docker Desktop 앱이 꺼져 있음 | Docker Desktop 앱을 실행하고 30초~1분 기다린 뒤 다시 시도 |
| `docker compose up` 시 "port is not available"/"Only one usage of each socket address" 오류 | 8080(Backend) 또는 3000(Frontend) 포트를 다른 프로세스가 이미 쓰고 있음(대부분 직접 켜둔 `gradlew bootRun`/`npm run dev`가 안 꺼진 경우) | PowerShell에서 `Get-NetTCPConnection -LocalPort 8080,3000 \| Select OwningProcess`로 점유 프로세스를 찾아 `Stop-Process -Id <PID> -Force`로 종료한 뒤 `docker compose up -d` 재시도 |
| `cd` 명령에서 "경로가 존재하지 않는다"는 오류 | 폴더 이름만 입력(`cd SingleONE`)해서 발생. PowerShell은 기본적으로 `C:\WINDOWS\system32`에서 시작함 | 이 문서에 적힌 것처럼 **전체 경로**를 큰따옴표로 감싸서 입력 |
| `npm run dev` 실행 시 "이 시스템에서 스크립트를 실행할 수 없다"는 보안 오류(PSSecurityException) | Windows PowerShell의 기본 보안 정책 때문(코드 문제 아님) | `npm run dev` 대신 **`npm.cmd run dev`**로 실행 |
| Backend 실행 시 MySQL 접속 실패(Access denied) 오류 | PowerShell은 `.env` 파일을 자동으로 읽지 않음 | 이미 `backend/build.gradle`에 `.env`를 자동으로 읽어오도록 조치돼 있어 보통 발생하지 않지만, 계속되면 `.env` 파일의 `MYSQL_PASSWORD` 값과 Docker 컨테이너가 실제로 쓰는 값이 같은지 확인 |
| Backend/Frontend 터미널이 아무 반응 없이 멈춘 것처럼 보임 | 정상입니다. 서버는 계속 켜진 채로 대기하는 프로그램이라 새 로그가 안 올라오는 것이 원래 동작 | 브라우저로 `http://localhost:3000` 또는 `http://localhost:8080/actuator/health`에 접속해 실제로 응답하는지 확인 |
| 업로드한 파일이 "실패"로 표시됨 | CSV 안의 특정 행에 형식 오류(음수 값, 필수 항목 누락, 지원하지 않는 날짜 형식 등)가 있음 | 업로드 이력에서 "오류 상세" 버튼을 눌러 어떤 행에 어떤 문제가 있는지 확인 후 수정해서 재업로드 |
| Dashboard에 "이 광고주에는 아직 프로젝트가 없습니다" 표시 | 성과 데이터는 올렸지만 프로젝트를 아직 안 만듦 | `/projects`에서 최소 2개 이상 매체를 포함한 프로젝트를 먼저 생성 |

---

## 6. 데이터를 초기화하는 방법

**주의: 아래 명령은 지금까지 넣은 모든 데이터(업로드 이력, 프로젝트 등)를 완전히 삭제합니다.** 정말 처음부터 다시 시작하고 싶을 때만 사용하세요.

1. 백엔드/프론트엔드 터미널을 먼저 끕니다(위 "3. 프로그램을 끄는 순서" 1~2번).
2. 아래 명령으로 데이터베이스를 완전히 초기화합니다.
   ```powershell
   cd "C:\Users\ingi.lee\Desktop\A1mediagroup\회사 자료\9996. 해커톤\SingleONE\infra"
   docker compose --env-file ../.env -f docker-compose.yml down -v
   docker compose --env-file ../.env -f docker-compose.yml up -d
   ```
   (`down -v`의 `-v`가 저장된 데이터까지 지우는 부분입니다. `-v` 없이 `down`만 하면 컨테이너만 지우고 데이터는 남습니다.)
3. Backend를 다시 켜면(위 "1-2") 빈 데이터베이스에 테이블이 자동으로 새로 만들어집니다.
4. 이후 "4. 테스트 데이터 넣는 방법"을 다시 따라 하면 됩니다.

---

## 7. 자동 테스트 실행 (선택 사항, 개발자용)

```powershell
# Backend
cd "C:\Users\ingi.lee\Desktop\A1mediagroup\회사 자료\9996. 해커톤\SingleONE\backend"
./gradlew test

# Frontend
cd "C:\Users\ingi.lee\Desktop\A1mediagroup\회사 자료\9996. 해커톤\SingleONE\frontend"
npm.cmd test

# 브라우저 자동 확인(E2E, Docker+Backend+Frontend가 모두 켜진 상태에서 실행)
npx playwright test --workers=1
```

Backend 테스트 중 일부(Testcontainers 기반)는 이 개발 환경의 Docker Desktop 버전과의 호환 문제로 자동 실행되지 않을 수 있습니다 — 코드 결함이 아니라 알려진 환경 제약이며, 자세한 내용은 `docs/FINAL_VALIDATION_REPORT.md`를 참고하세요.
