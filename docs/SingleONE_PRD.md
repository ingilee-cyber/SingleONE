**SingleONE**

**Product Requirements Document (PRD)**

테스트 제품 최종 확정본

| **항목** | **내용** |
|----|----|
| 문서 버전 | v1.0 |
| 기준일 | 2026-08-12 |
| 제품명 | SingleONE |
| 핵심 체계 | SingleONE Index |
| 목적 | 매체별 원본 성과를 SingleONE 공통 전환 기준으로 재측정하고, 상대 효율 비교·하위 성과 분석·사용자 여정 분석·예산 시뮬레이션을 제공하는 테스트 제품 |
| 구현 대상 | Claude Code 기반 테스트 제품 |

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr>
<th><strong>문서 상태</strong><br />
본 문서는 1~7단계 요구사항 수집, 최종 역검증 및 추가 권장사항 확정까지 반영한 구현 기준 문서이다. 과거의 “비교 그룹”, “저장된 시나리오”, “자동 예산 추천/배분” 요구사항은 폐기된 상태로 본 문서에 포함하지 않는다.</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

# 목차

1\. 제품 개요 및 목표

2\. 범위와 비범위

3\. 용어 및 분석 계층

4\. 사용자·내비게이션·공통 UX

5\. 프로젝트(Project) 요구사항

6\. Dashboard 요구사항

7\. 매체/캠페인/광고 그룹/광고 상세

8\. SingleONE 성과 및 SingleONE Index 계산

9\. Journey & Attribution

10\. Media Planning Simulation

11\. 데이터 관리 및 업로드

12\. 논리 데이터 모델

13\. API 및 기술 아키텍처

14\. 비기능 요구사항

15\. 테스트 데이터

16\. Acceptance Criteria

17\. 구현 제외/향후 확장

18\. Claude Code 구현 체크리스트

# 1. 제품 개요 및 목표

## 1.1 제품 정의

SingleONE은 서로 다른 디지털 광고 매체의 상이한 전환 측정 기준으로 인해 발생하는 성과 차이를 SingleONE 고유의 공통 전환 측정 체계로 재측정하고, 매체 간 상대 효율을 비교할 수 있게 하는 분석 제품이다.

테스트 제품의 핵심 검증 대상은 “SingleONE 성과를 제공하고 이를 이용해 비교·분석·예산 시뮬레이션하는 제품 구조가 유용한가”이다. 실제 SingleONE 전환 필터링 알고리즘의 정확도 자체는 본 테스트 범위에서 검증하지 않는다.

## 1.2 핵심 문제

- 광고 매체마다 전환 인정 기준, 집계 방식, attribution window 등이 달라 매체 관리자 화면의 성과를 단순 비교하기 어렵다.

- 매체별 원본 전환 수치를 단순 합산하면 동일한 기준의 상대 효율 비교가 되지 않는다.

- 광고주는 공통 기준에서 각 매체의 실제 상대 효율과 하위 운영 단위의 성과를 확인할 필요가 있다.

## 1.3 핵심 가치

- 원본 매체 성과와 SingleONE 성과를 동시에 제공하여 출처 성과와 공통 측정 성과를 분리해 이해할 수 있다.

- SingleONE Index 점수로 동일 프로젝트 내 매체 간 상대 효율을 평균 100 기준으로 비교한다.

- 매체 → 캠페인 → 광고 그룹 → 광고로 상세 분석을 내려갈 수 있다.

- Journey & Attribution을 통해 클릭 기반 구매 여정, 채널 기여도, 채널 페어를 분석한다.

- 사용자가 직접 입력한 예산 시나리오의 예상 결과를 시뮬레이션하되, 예산 증감이나 최적 배분을 직접 추천하지 않는다.

## 1.4 주요 사용자

디지털/온라인 마케팅을 운영하거나 분석하는 브랜드 마케터, 광고대행사 AE, 퍼포먼스 마케터, 브랜드 운영자 등을 대상으로 한다.

# 2. 범위와 비범위

## 2.1 테스트 제품 범위

| **기능** | **포함 여부** | **설명** |
|----|----|----|
| Dashboard | 포함 | 프로젝트·기간 기준 원본/SingleONE 성과와 매체 Index 비교 |
| 프로젝트 | 포함 | 여러 매체의 캠페인을 동일 분석 목적 아래 묶는 SingleONE 전용 비교 컨텍스트 |
| 상세 분석 | 포함 | 매체 → 캠페인 → 광고 그룹 → 광고의 별도 상세 화면 |
| Media Planning Simulation | 포함 | 사용자 입력 예산안의 예상 SingleONE 성과 분석 |
| Journey & Attribution | 포함 | 사용자 여정 / 채널별 전환 기여도 / 채널 페어 인사이트 |
| 데이터 관리 | 포함 | CSV/XLSX 성과 및 Journey 이벤트 업로드, 이력·오류 상세 |

## 2.2 명시적 제외 사항

| **제외 기능** | **처리** |
|----|----|
| 실제 광고 매체 API 연동 | 제외. 테스트 데이터는 CSV/XLSX 업로드 |
| 로그인/회원가입/권한 관리 | 제외. Spring Security 구조만 유지하고 테스트 버전은 인증 없이 접근 |
| 실제 광고 플랫폼 예산 변경/적용 | 제외 |
| 매체별 예산 자동 추천/배분 | 제외 |
| 구매수 최대화/매출 최대화 목표 선택 | 제외 |
| 증액 추천/감액 추천/최적 예산 문구 | 제외 |
| 신규 매체 자동 탐색 예산 | 제외 |
| SingleONE Index 기반 자동 fallback 배분 | 제외 |
| 저장된 시나리오/Snapshot/시나리오 비교 | 제외 |
| 원본 데이터 개별 행 수정/삭제 UI | 제외 |
| 업로드 Rollback | 제외 |
| 실제 cross-platform identity stitching | 제외. 테스트 이벤트의 synthetic anonymous_user_id 사용 |
| 실제 SingleONE 필터링 알고리즘 | 제외. 매체별 고정 내부 테스트 필터율 사용 |

# 3. 용어 및 분석 계층

## 3.1 용어 정의

| **용어** | **정의** |
|----|----|
| SingleONE Index | SingleONE의 고유 공통 전환 측정/필터링 체계. 단순 점수 공식만을 의미하지 않는다. |
| SingleONE 성과 | SingleONE Index 기준을 적용해 재측정된 구매, 구매매출, CPA, ROAS |
| SingleONE 구매 ⓘ | SingleONE 기준 구매. hover 문구는 “자체 내부 전환 기준입니다.” |
| SingleONE Index 점수 | 동일 프로젝트·기간의 유효 매체들을 상대 비교하기 위한 효율 점수. 유효 매체 평균은 100 |
| 원본 성과 | 각 광고 매체/업로드 데이터에서 제공된 객관적 원본 성과 |
| 프로젝트(Project) | 여러 매체의 캠페인을 동일 분석 목적/맥락으로 묶는 SingleONE 전용 분석 컨테이너 |
| 기여 구매 | Journey 이벤트 데이터에 Linear Attribution을 적용해 채널에 배분한 fractional purchase credit |

## 3.2 최종 분석 계층

광고주\
└─ 프로젝트\
└─ 매체\
└─ 캠페인\
└─ 광고 그룹\
└─ 광고

프로젝트는 광고 플랫폼 내부의 광고 그룹과 혼동을 피하기 위해 기존 “비교 그룹” 명칭을 대체한다. 프로젝트는 실제 광고 플랫폼 계층이 아니라 SingleONE의 비교 컨텍스트이다.

## 3.3 계층별 제공 범위

| **계층** | **원본 성과** | **SingleONE 성과** | **SingleONE Index 점수** | **이전 기간 비교** | **7일 Rolling Index** |
|----|----|----|----|----|----|
| 프로젝트 | 집계 가능 | 집계 가능 | X | \- | \- |
| 매체 | O | O | O | O | O |
| 캠페인 | O | O | X | O | X |
| 광고 그룹 | O | O | X | X | X |
| 광고 | O | O | X | X | X |

# 4. 사용자·내비게이션·공통 UX

## 4.1 메인 내비게이션

| **순서** | **메뉴** | **역할** |
|----|----|----|
| 1 | Dashboard | 매체 성과와 SingleONE Index 비교의 시작점 |
| 2 | 프로젝트 | 프로젝트 생성/수정/삭제 및 캠페인 구성 |
| 3 | Media Planning Simulation | 사용자 입력 예산 시나리오의 예상 성과 분석 |
| 4 | Journey & Attribution | 구매 여정/기여도/채널 페어 분석 |
| 5 | 데이터 관리 | 성과/Journey 데이터 업로드 및 이력 확인 |

매체/캠페인/광고 그룹/광고 상세는 메인 메뉴가 아니라 Dashboard 또는 상위 상세 화면에서 진입하는 분석 상세 Route로 제공한다.

## 4.2 공통 분석 컨텍스트

- 광고주, 프로젝트, 기간을 핵심 분석 컨텍스트로 사용한다.

- Dashboard에서 매체를 클릭하여 상세 화면으로 이동할 때 광고주/프로젝트/기간/이전 기간 비교 상태를 유지한다.

- 상세 화면은 Breadcrumb을 제공하며 상위 계층으로 이동 가능하다.

- 대용량 목록은 서버 Pagination 기본 50개, 최대 200개를 사용한다. 검색/정렬은 Backend에서 처리한다.

## 4.3 공통 상태

- Loading: Skeleton/Progress 등 명확한 로딩 상태 제공

- Empty: 해당 프로젝트/기간에 데이터가 없음을 안내

- Error: 서버/처리 실패 사유를 사용자에게 이해 가능한 수준으로 제공

- Data shortage: “데이터 부족”, “필수 데이터 누락”, “비교 가능한 매체 부족”, “이전 기간 데이터 부족”, “예측 불가”를 구분한다.

# 5. 프로젝트(Project) 요구사항

## 5.1 프로젝트 규칙

- 프로젝트는 광고주 단위로 관리한다.

- 프로젝트명은 동일 광고주 내에서 unique 해야 한다.

- 프로젝트는 기간을 저장하지 않는다. Dashboard/Journey/Simulation에서 기간을 독립 선택한다.

- 프로젝트 구성 단위는 캠페인이다. 선택한 캠페인의 하위 광고 그룹과 광고는 자동 포함한다.

- 프로젝트에는 최소 2개의 서로 다른 매체가 포함되어야 하며 각 매체에는 최소 1개 캠페인이 선택되어야 한다.

- 동일 캠페인은 하나의 프로젝트 안에서 한 번만 선택 가능하지만 서로 다른 프로젝트에는 중복 포함할 수 있다.

- 프로젝트 수정 시 project_id는 유지한다.

- 프로젝트 삭제 시 확인 Modal을 표시한다.

## 5.2 시스템 기본 프로젝트

- 광고주별 시스템 기본 \`전체 캠페인\` 프로젝트를 제공한다.

- 모든 업로드 캠페인을 포함하는 참고용 비교이며 read-only / non-deletable이다.

- 캠페인 목적이 혼재할 수 있으므로 UI에 \`참고용 비교\`임을 명시한다.

- Dashboard 및 Journey에서 사용할 수 있으나 Media Planning Simulation에서는 선택할 수 없다.

## 5.3 캠페인 선택 UX

- 광고주의 업로드된 전체 캠페인 목록을 표시한다.

- 캠페인명/ID 검색 및 매체 필터를 제공한다.

- 표시 이름은 가장 최신 date의 campaign_name을 사용하며 동일 date라면 최신 SUCCESS upload batch의 name을 사용한다.

## 5.4 객체 식별 규칙

| **객체** | **Natural/Business Identity** |
|----|----|
| Campaign | advertiser_id + media + campaign_id |
| Ad Group | advertiser_id + media + campaign_id + ad_group_id |
| Ad | advertiser_id + media + campaign_id + ad_group_id + ad_id |
| 프로젝트의 캠페인 참조 | advertiser_id + media + campaign_id 복합키를 참조 |

# 6. Dashboard 요구사항

## 6.1 목적 및 기본 동작

Dashboard는 선택한 광고주/프로젝트/기간의 원본 성과와 SingleONE 성과를 비교하고, 프로젝트 내 매체의 SingleONE Index 점수를 중심으로 상대 효율을 파악하는 첫 화면이다.

- 기본 기간: 최근 30일

- 이전 기간 비교: 기본 ON

- 기간 Quick option: 최근 7일 / 최근 30일 / 이번 달 / 지난 달 / 직접 설정

- 광고주 변경 시 사용 가능한 프로젝트를 다시 조회하고 유효하지 않은 기존 project 선택은 해제한다.

## 6.2 KPI 카드

| **지표**         | **표시 원칙**             |
|------------------|---------------------------|
| Cost             | 원본만 표시               |
| Impressions      | 원본만 표시               |
| Clicks           | 원본만 표시               |
| Purchases        | 원본 + SingleONE 구매 ⓘ   |
| Purchase Revenue | 원본 + SingleONE 구매매출 |
| ROAS             | 원본 + SingleONE ROAS     |

Dashboard KPI는 사용자가 show/hide만 할 수 있다. Drag & Drop 재배치는 제공하지 않으며 표시 설정은 브라우저 localStorage에 저장한다. 지표를 숨겨도 Index 계산에는 영향을 주지 않는다.

## 6.3 Dashboard 구성

1.  상단 KPI 카드

2.  매체별 SingleONE Index 점수 차트/목록: 점수 높은 순 정렬

3.  원본 + SingleONE 효율 성과 테이블: 컬럼별 오름차순/내림차순 정렬

4.  SingleONE Index 구성요소 Breakdown

5.  7일 Rolling SingleONE Index Line Chart

6.  Journey & Attribution 요약: 주요 사용자 여정, 1위 전환 기여 채널, 주요 채널 페어, 상세 분석 이동 버튼

## 6.4 Index 안내

SingleONE Index 점수 옆 ⓘ tooltip 문구: “비교 프로젝트 내 유효 매체들의 비용 대비 광고 효율 평균을 100으로 환산한 상대 효율 점수입니다. 100보다 높을수록 비교 대상 매체 평균보다 상대적으로 높은 효율을 의미합니다.”

구매/매출 효율은 SingleONE 공통 전환 측정 기준으로 재측정된 SingleONE 성과를 사용한다.

# 7. 매체/캠페인/광고 그룹/광고 상세

## 7.1 Route 및 탐색 구조

Dashboard\
→ 매체 상세\
→ 캠페인 상세\
→ 광고 그룹 상세\
→ 광고 상세

하나의 펼침형 계층 테이블이 아니라 계층별 별도 상세 화면을 사용한다. 공통 상세 컴포넌트 패턴을 재사용하고 Breadcrumb으로 상위 계층에 복귀한다.

## 7.2 매체 상세

- 광고주 / 프로젝트 / 기간 / 매체명을 상단 컨텍스트로 표시

- SingleONE Index 점수, 이전 기간 점수, 변화량

- 원본 vs SingleONE 성과: 비용, 구매, 구매매출, CPA, ROAS

- SingleONE 구매 옆 ⓘ hover: “자체 내부 전환 기준입니다.”

- SingleONE Index 구성요소 Breakdown

- 7일 Rolling SingleONE Index

- 캠페인 목록 및 이름/ID 검색, 컬럼 정렬, 원본/SingleONE 성과 표시

## 7.3 캠페인 상세

- 원본 성과와 SingleONE 성과 제공

- 이전 기간 비교 제공

- SingleONE Index 점수는 제공하지 않음

- 광고 그룹 목록과 검색/정렬 제공

## 7.4 광고 그룹 상세

- 원본 성과와 SingleONE 성과 제공

- 이전 기간 비교 및 Index 점수 미제공

- 광고 목록과 검색/정렬 제공

## 7.5 광고 상세

- 원본 성과와 SingleONE 성과 제공

- Index 점수/이전 기간 비교/하위 객체 목록은 제공하지 않음

## 7.6 하위 계층 최소 데이터 표시

SingleONE Index의 최소 구매/운영일/비용 조건은 매체 점수 산출에만 적용한다. 캠페인·광고 그룹·광고의 SingleONE 구매가 작더라도 해당 성과값 자체는 숨기지 않는다.

# 8. SingleONE 성과 및 SingleONE Index 계산

## 8.1 지원 매체

테스트 제품의 초기 지원 매체는 Meta, TikTok, Google, Naver, Criteo 5개다.

## 8.2 원본 성과 필드

| **필드**         | **사용**                    |
|------------------|-----------------------------|
| impressions      | Index 노출 효율 및 KPI      |
| clicks           | Index 클릭 효율 및 KPI      |
| cost             | 모든 효율 계산 기준 비용    |
| add_to_cart      | 참고 지표. Index에는 미사용 |
| purchases        | SingleONE 필터링 대상       |
| purchase_revenue | SingleONE 필터링 대상       |

## 8.3 내부 테스트 필터율

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr>
<th><strong>INTERNAL ONLY</strong><br />
아래 비율은 실제 고객 UI에 절대 노출하지 않는다. 테스트용 SingleONE 필터링 로직을 deterministic하게 검증하기 위한 내부 설정이다.</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

| **매체** | **고정 테스트 필터율** |
|----------|------------------------|
| Meta     | 65%                    |
| TikTok   | 62%                    |
| Google   | 69%                    |
| Naver    | 64%                    |
| Criteo   | 61%                    |

- 필터율은 코드 if/else 하드코딩이 아니라 Backend 비공개 내부 설정으로 관리한다.

- 동일 매체에서는 purchases와 purchase_revenue에 동일 필터율을 적용한다.

- 질의마다 랜덤한 비율을 생성하지 않는다.

## 8.4 SingleONE 성과 계산

SingleONE purchases = source purchases × media_filter_rate\
SingleONE purchase_revenue = source purchase_revenue × media_filter_rate\
SingleONE CPA = cost ÷ SingleONE purchases\
SingleONE ROAS = SingleONE purchase_revenue ÷ cost × 100

- 공식 매체 성과는 선택 프로젝트·기간의 포함 캠페인 원본을 매체 단위로 집계한 뒤 필터율을 적용한다.

- 캠페인/광고 그룹/광고에는 동일 매체 필터율을 적용하여 하위 SingleONE 성과를 제공한다. 내부 반올림을 하지 않아 하위 합계와 상위 합계가 일치하도록 한다.

- SingleONE 구매는 내부 소수점을 유지하고 화면에서만 정수로 반올림한다.

- SingleONE 구매매출은 내부 정밀도를 유지한다.

- SingleONE purchases가 0이면 CPA는 \`-\`, ROAS는 0%로 표시한다.

## 8.5 SingleONE Index 점수 공식

| **구성요소** | **효율**                          | **가중치** |
|--------------|-----------------------------------|------------|
| 노출 효율    | impressions / cost                | 10%        |
| 클릭 효율    | clicks / cost                     | 20%        |
| 구매 효율    | SingleONE purchases / cost        | 35%        |
| 매출 효율    | SingleONE purchase_revenue / cost | 35%        |

component_index = media_efficiency / mean_efficiency_of_valid_media × 100\
\
SingleONE Index score\
= exposure_index × 0.10\
+ click_index × 0.20\
+ purchase_index × 0.35\
+ revenue_index × 0.35

- 점수 산출이 가능한 유효 매체들의 최종 Index 점수 평균은 항상 100이다.

- 점수 상·하한 cap은 두지 않는다.

- 내부 점수는 소수 정밀도로 보관하고 Dashboard에서는 정수 반올림한다.

- 점수는 절대 성과 등급이 아니라 프로젝트·기간 내 상대 효율이다. 다른 매체 성과 변화만으로도 특정 매체 점수가 변할 수 있다.

## 8.6 Index 산출 최소 조건

| **조건**            | **기준**                 |
|---------------------|--------------------------|
| 실제 운영일         | 7일 이상                 |
| Cost                | 1,000,000 KRW 이상       |
| SingleONE purchases | 내부 소수값 기준 10 이상 |

운영일은 프로젝트 포함 캠페인 중 해당 매체의 cost \> 0인 distinct date 수다. cost=0인 날짜는 기간 총합에는 포함될 수 있으나 운영일 수에는 포함하지 않는다.

- 조건 미달 시 원본/SingleONE 성과는 표시하되 Index는 \`데이터 부족\`으로 표시한다.

- 필수 Index 원본 필드가 누락된 매체는 가중치 재분배 없이 전체 Index를 계산하지 않고 \`필수 데이터 누락\`으로 표시한다.

- 유효 비교 매체가 1개 이하이면 \`비교 가능한 매체 부족\`으로 표시한다.

- 유효하지 않은 매체를 제외한 나머지 유효 매체만으로 평균=100을 재계산한다.

## 8.7 효율 참고 지표

사용자-facing 효율 지표는 CPM, CPC, CPA, ROAS를 제공한다. 매체 API에서 제공된 ROAS 값을 신뢰하지 않고 저장된 원본 필드로 계산한다.

## 8.8 이전 기간 비교

- 선택 기간과 동일한 길이의 바로 직전 기간을 기본 이전 기간으로 사용한다.

- 동일 프로젝트 및 동일 매체 구성 기준으로 비교한다.

- 이전 기간 매체가 Index 조건 미충족이면 \`이전 기간 데이터 부족\`으로 표시한다.

- Index 변화뿐 아니라 원본 및 SingleONE 성과 변화를 함께 보여준다.

## 8.9 7일 Rolling Index

- 날짜 D의 Rolling window는 D-6일부터 D까지의 7일이다.

- 각 7일 원본 데이터를 매체 단위로 집계 → SingleONE 필터 → Index 계산 순서로 수행한다.

- 각 window에도 동일 최소 데이터 조건을 적용한다.

- 해당 window 유효 매체가 1개 이하이면 점수를 표시하지 않는다.

- 사용자 선택 기간 밖의 과거 데이터도 window 계산에 사용할 수 있지만, 차트에는 선택 기간 날짜의 point만 표시한다.

# 9. Journey & Attribution

## 9.1 화면 구조

독립 메인 화면 \`Journey & Attribution\`을 제공하고 다음 3개 탭으로 구성한다.

| **탭**             | **주요 출력**                                         |
|--------------------|-------------------------------------------------------|
| 사용자 여정        | Flow/Sankey + 구매수 기준 Top 20 Path 테이블          |
| 채널별 전환 기여도 | 기여 구매, 기여 구매매출, 기여 비중                   |
| 채널 페어 인사이트 | 함께 등장한 구매 여정 수, 구매매출, 전체 구매 중 비중 |

## 9.2 공통 필터

- 광고주 → 프로젝트 → 기간 필터를 3개 탭 공통으로 사용한다.

- Dashboard의 Journey 요약에서 상세 화면으로 이동할 때 동일 필터 상태를 유지한다.

## 9.3 이벤트/여정 정의

- 사용자 식별자: anonymous_user_id

- 광고 접점: CLICK 이벤트만 인정

- 최종 전환: PURCHASE

- Journey window: 구매 전 7일

- Attribution: Linear Attribution

- 하나의 구매 여정에 참여한 unique channel 각각에 동일한 fractional credit을 부여한다.

- 동일 채널의 반복 클릭은 Attribution 가중치를 추가하지 않는다.

- 연속된 동일 채널 클릭은 raw event로 보존하지만 Journey 시각화에서는 하나의 touchpoint로 압축한다.

- 채널 페어는 방향이 없는 unordered combination으로 본다. Meta+Google과 Google+Meta는 동일 pair다.

- 동일 사용자의 여러 구매는 각각 별도 Journey로 분리한다.

- 각 구매 Journey 시작 시점은 max(구매시점-7일, 직전 구매시점)이다. 이전 구매 전의 클릭을 다음 구매 Journey에 재사용하지 않는다.

## 9.4 프로젝트 적용 규칙

선택 프로젝트에 포함된 campaign의 CLICK 이벤트만 해당 프로젝트 Journey의 eligible touchpoint로 인정한다. PURCHASE 이벤트 자체는 특정 media/campaign에 귀속시키지 않는다.

## 9.5 이벤트 스키마

| **이벤트** | **Required** | **Optional/비고** |
|----|----|----|
| CLICK | event_id, anonymous_user_id, event_timestamp, advertiser_id, media, campaign_id, event_type=CLICK | ad_group_id, ad_id |
| PURCHASE | event_id, anonymous_user_id, event_timestamp, advertiser_id, event_type=PURCHASE, order_id, purchase_revenue | media/campaign_id/ad_group_id/ad_id는 비움 |

## 9.6 Attribution 출력

예: TikTok → Google → Meta → PURCHASE인 경우 각 unique channel에 구매 1/3과 구매매출 1/3을 부여한다.

Attribution 탭에서 \`SingleONE 기여 구매\`라는 표현은 사용하지 않는다. Journey는 이벤트 데이터 기반 분석이며 Dashboard의 SingleONE 성과와 집계값이 다를 수 있음을 안내한다.

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr>
<th><strong>Journey 안내 문구</strong><br />
Journey &amp; Attribution 분석은 이벤트 데이터를 기준으로 하며 Dashboard의 SingleONE 성과와 집계값이 다를 수 있습니다.</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

## 9.7 Channel Pair 표현 원칙

- 빈도·구매매출·비중은 제공한다.

- “최적 조합”, “가장 효율적인 조합”, “이 조합 때문에 성과가 향상됨” 등 인과적 표현은 사용하지 않는다.

- “가장 많이 관찰된 채널 페어”, “구매 여정에서 동시 등장 빈도가 높은 조합”처럼 중립적으로 표현한다.

# 10. Media Planning Simulation

## 10.1 기능 목적

SingleONE이 예산을 직접 추천·배분하거나 성과를 보장하지 않고, 사용자가 직접 입력한 매체별 예산안이 과거 SingleONE 성과 모델 기준에서 어떤 예상 결과를 보이는지 분석하는 의사결정 지원 기능이다.

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr>
<th><strong>표현 원칙</strong><br />
“추천 예산”, “증액 추천”, “감액 추천”, “최적 예산”, “구매 최대화”, “매출 최대화” 등의 지시적/최적화 표현을 사용하지 않는다.</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

## 10.2 입력

| **입력 항목** | **규칙** |
|----|----|
| 프로젝트 | 사용자가 생성한 프로젝트만 선택 가능. 시스템 \`전체 캠페인\` 프로젝트는 참고 분석 전용 |
| 기준 성과 기간 | 현재 운영 성과 비교의 기준 기간 |
| 시뮬레이션 기간 | 예상 결과를 산출할 기간 |
| 매체별 예산 | 사용자가 각 매체에 직접 입력 |
| 총예산 | 매체별 입력 예산을 자동 합산. 사용자가 별도 최적화 목표는 선택하지 않음 |

## 10.3 환산 현재 운영

환산 현재 운영 예산 = 기준 성과 기간의 매체 Cost ÷ 기준 기간 일수 × 시뮬레이션 기간 일수

화면 Tooltip: “선택한 기준 기간의 일평균 광고비를 시뮬레이션 기간 길이에 맞춰 환산한 참고값입니다.”

## 10.4 주간 한계 효율 모델

- 주 단위로 historical performance를 집계한다.

- 기준 성과 기간 종료일을 기준으로 최근 8주 데이터를 모델 후보 window로 사용한다.

- 최소 6개 valid week와 SingleONE purchases 합계 100 이상이 필요하다.

- 매체마다 구매 모델과 구매매출 모델 2개를 생성한다.

y = a × ln(x) + b\
\
x = weekly cost\
y₁ = weekly SingleONE purchases\
y₂ = weekly SingleONE purchase_revenue\
fit = OLS

## 10.5 Valid week 및 모델 유효성

- Valid week는 cost\>0, impressions\>0, clicks\>0, SingleONE purchases\>0, SingleONE purchase_revenue\>=0이며 필요한 원본 필드 누락이 없어야 한다.

| **유효성 조건**     | **기준**                                  |
|---------------------|-------------------------------------------|
| 유효 주차           | 6주 이상                                  |
| SingleONE 구매 합계 | 100 이상                                  |
| 비용 변동           | max weekly cost / min weekly cost \>= 1.2 |
| 모델 적합도         | R² \>= 0.50                               |
| 곡선 형태           | 증가형 및 한계 효율 감소형(a\>0)          |
| 2개 모델            | 구매·구매매출 모델이 모두 유효해야 함     |

2개 모델 중 하나라도 유효하지 않으면 해당 매체 전체를 \`예측 불가\`로 표시한다. SingleONE Index 기반 자동 fallback은 사용하지 않는다.

## 10.6 기간/예산 변환

weekly_sim_budget = media_input_budget ÷ simulation_days × 7\
period_prediction = weekly_prediction × simulation_days ÷ 7

회귀식의 예상값이 음수인 경우 0으로 보정한다.

## 10.7 예측 범위 및 신뢰도

| **조건** | **처리** |
|----|----|
| 입력 예산 = 0 | 예상 성과 0. 전체 KPI 산출을 막지 않음 |
| 0 \< weekly budget \< 과거 최소 weekly cost | 예측 가능, 신뢰도 낮음 |
| 과거 최소~최대 운영 범위 내 | 정상 예측 |
| 과거 최대 초과~과거 최대의 150% 이하 | 예측 가능, 신뢰도 낮음 |
| 과거 최대의 150% 초과 | 예측 불가 |

| **신뢰도** | **기준** |
|----|----|
| 높음 | R²\>=0.75 + 유효 8주 + max/min 비용 변동폭 30% 이상(\>=1.3) + 시뮬레이션 weekly budget이 과거 운영 범위 내 |
| 보통 | 모델 유효하나 “높음” 조건을 모두 만족하지 않으며 예산이 과거 운영 범위 내 |
| 낮음 | 모델은 유효하나 과거 운영 범위 밖의 허용된 외삽/내삽 하단 구간 |
| 예측 불가 | 모델 무효 또는 weekly budget이 과거 최대의 150% 초과 |

## 10.8 결과

- 현재/환산 현재 운영 vs 사용자 입력 시나리오 비교

- 매체별 예상 SingleONE 구매

- 매체별 예상 SingleONE 구매매출

- 매체별 예상 SingleONE CPA

- 매체별 예상 SingleONE ROAS

- 매체별 한계 효율 곡선: 현재/입력 예산, 과거 운영 범위, 150% 외삽 한계 표시

- 예측 신뢰도

- 중립적 관찰 문구: “효율 감소 관찰”, “과거 운영 범위 초과”, “데이터 부족”, “포화구간 진입 가능성” 등

예산이 0보다 큰 매체 중 하나라도 \`예측 불가\`이면 전체 예상 SingleONE 구매/구매매출/CPA/ROAS는 \`산출 불가\`로 표시한다. 예측 가능한 매체의 개별 결과는 계속 제공한다.

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr>
<th><strong>면책 문구</strong><br />
예상 성과는 과거 운영 데이터를 기반으로 한 시뮬레이션 값이며 실제 광고 성과를 보장하지 않습니다.</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

## 10.9 상태 저장

- 시뮬레이션 입력/결과는 DB에 저장하지 않는다.

- LocalStorage에도 저장하지 않는다.

- 현재 페이지 세션 동안 Zustand에만 임시 유지하며 새로고침/재접속 시 초기화한다.

# 11. 데이터 관리 및 업로드

## 11.1 업로드 종류

| **종류** | **파일** | **목적** |
|----|----|----|
| 성과 데이터 | CSV / .xlsx | Dashboard, SingleONE 성과, Index, 상세 분석, Simulation |
| Journey 이벤트 데이터 | CSV / .xlsx | Journey & Attribution |

- .xlsx는 첫 번째 worksheet만 읽는다.

- CSV는 UTF-8을 권장한다.

- 통화는 KRW로 고정하며 금액은 입력값을 그대로 사용한다.

## 11.2 성과 데이터 Grain 및 필드

성과 데이터의 기본 Grain은 \`일자 × 광고(Ad)\`이다.

| **필드**                        | **설명**                        |
|---------------------------------|---------------------------------|
| date                            | 성과 일자                       |
| advertiser_id / advertiser_name | 광고주                          |
| media                           | Meta/TikTok/Google/Naver/Criteo |
| campaign_id / campaign_name     | 캠페인                          |
| ad_group_id / ad_group_name     | 광고 그룹                       |
| ad_id / ad_name                 | 광고                            |
| impressions / clicks / cost     | 노출/클릭/비용                  |
| add_to_cart                     | 참고 전환                       |
| purchases / purchase_revenue    | 구매/구매매출                   |

## 11.3 성과 데이터 고유키

date + advertiser_id + media + campaign_id + ad_group_id + ad_id

## 11.4 Journey 데이터 고유키

- Event unique key: advertiser_id + event_id

- order_id는 동일 광고주 내에서 unique해야 한다.

## 11.5 검증 규칙

- 필수 컬럼 존재 여부

- 날짜/시간 형식 유효성

- numeric type 유효성

- 음수 cost/impressions/clicks/add_to_cart/purchases/purchase_revenue 금지

- 지원하지 않는 media 금지

- 파일 내부 동일 natural key 중복은 오류로 처리

- 오류가 하나라도 존재하면 전체 파일 반영을 취소하고 row-specific error를 제공

## 11.6 기존 데이터 중복/Overwrite

기존 SUCCESS 데이터와 natural key가 중복되는 경우 즉시 덮어쓰지 않고 사용자 확인을 요구한다. 사용자가 확인하면 최신 SUCCESS upload batch가 동일 natural key의 유효값이 된다.

## 11.7 업로드 상태 머신

VALIDATING\
├─ validation error → FAILED\
├─ existing duplicate → DUPLICATE_CONFIRMATION_REQUIRED\
│ ├─ confirm → IMPORTING → SUCCESS / FAILED\
│ └─ cancel → CANCELLED\
└─ no duplicate → IMPORTING → SUCCESS / FAILED

- SUCCESS batch만 분석에서 사용한다.

- MySQL/ClickHouse 간 부분 반영을 방지하기 위해 upload_batch_id를 사용한다.

- ClickHouse에 일부 적재되더라도 batch가 SUCCESS가 아니면 분석에서 활성화하지 않는다.

## 11.8 비동기 처리

- 파일 업로드는 비동기 Job으로 처리한다.

- 화면은 검증 중 / 중복 확인 필요 / 반영 중 / 성공 / 실패 / 취소 상태를 표시한다.

## 11.9 업로드 이력

업로드 일시, 데이터 종류, 파일명, 광고주, 반영 건수, 상태, 오류 상세를 제공한다. 원본 파일 자체는 성공 후 영구 저장하지 않는다.

## 11.10 Master Upsert

성과 데이터 업로드가 SUCCESS가 되면 Advertiser/Campaign/Ad Group/Ad master를 자동 upsert한다. 이름 표시 우선순위는 최신 date이며 동일 date라면 최신 SUCCESS upload batch를 사용한다.

## 11.11 제한

- 파일당 최대 50MB

- 파일당 최대 1,000,000행

- Streaming 방식으로 파싱하여 전체 파일을 메모리에 적재하지 않는다.

- 원본 데이터 개별 행 수정/삭제 UI와 업로드 Rollback은 제공하지 않는다. 수정은 파일 재업로드로 처리한다.

# 12. 논리 데이터 모델

## 12.1 MySQL 영역

| **논리 Entity** | **주요 내용** |
|----|----|
| Advertiser | advertiser_id, latest advertiser_name |
| Campaign Master | advertiser_id, media, campaign_id, latest name |
| Ad Group Master | advertiser_id, media, campaign_id, ad_group_id, latest name |
| Ad Master | advertiser_id, media, campaign_id, ad_group_id, ad_id, latest name |
| Project | project_id, advertiser_id, project_name, system_default flag, reference_only flag |
| ProjectCampaign | project_id + campaign composite reference |
| UploadBatch | upload_batch_id, type, filename, status, counts, timestamps |
| UploadError | upload_batch_id, row_no, error_code, message |
| InternalMediaFilter | media, filter_rate (Backend-only/non-public) |

## 12.2 ClickHouse 영역

| **분석 Entity** | **주요 내용** |
|----|----|
| Performance Fact | 일자×광고 원본 성과 + upload_batch_id |
| Journey Event | CLICK/PURCHASE event + upload_batch_id |
| 분석 집계 | Dashboard/Index/Rolling/Attribution용 조회 또는 materialized aggregation |

## 12.3 DB 역할 분리

- MySQL: 운영/관리/master/project/upload metadata

- ClickHouse: 대용량 성과/event/분석 조회

- Spring Data JPA/Hibernate는 MySQL에만 적용한다.

- ClickHouse는 별도 Java Client/JDBC로 접근한다.

- SingleONE 필터/Index/Log model/R²/신뢰도 계산은 Java Backend가 담당하고 ClickHouse는 집계 및 분석 데이터 조회를 담당한다.

# 13. API 및 기술 아키텍처

## 13.1 Backend 기술 스택

| **구분**  | **기술**                                 |
|-----------|------------------------------------------|
| 언어      | Java 17                                  |
| Framework | Spring Boot 3.1.5                        |
| Modules   | Spring Data JPA, Spring Security         |
| DB        | MySQL + ClickHouse                       |
| ORM       | Hibernate / Spring Data JPA (MySQL only) |
| Build     | Gradle 8.4                               |

## 13.2 Frontend 기술 스택

| **구분**      | **기술**                               |
|---------------|----------------------------------------|
| 언어          | TypeScript 5                           |
| Framework     | Next.js 15.4.2 / React 19 / App Router |
| UI            | Material UI (MUI 7)                    |
| 상태 관리     | Zustand                                |
| HTTP          | Axios                                  |
| Charts        | Apache ECharts                         |
| Runtime/Build | Node.js 20+ / npm                      |

## 13.3 API 규격

- REST + JSON

- Base prefix: /api/v1

- OpenAPI/Swagger 문서 자동 생성

## 13.4 주요 API 그룹

| **Group** | **예시 Endpoint** |
|----|----|
| Advertiser | GET /api/v1/advertisers |
| Project | GET/POST /api/v1/projects, PUT/DELETE /api/v1/projects/{projectId} |
| Dashboard | GET /api/v1/dashboard |
| Details | GET /api/v1/media/{media}, /campaigns/{id}, /ad-groups/{id}, /ads/{id} |
| Journey | GET /api/v1/journey/paths, /attribution, /channel-pairs |
| Simulation | POST /api/v1/planning/simulate |
| Upload | POST /api/v1/uploads/performance, /journey; GET /api/v1/uploads |
| Duplicate confirmation | POST /api/v1/uploads/{batchId}/confirm-overwrite 또는 cancel |

상세 Endpoint path 및 DTO 필드명은 위 도메인/식별 규칙을 준수하며 OpenAPI를 Backend 계약의 source of truth로 사용한다.

## 13.5 Security

- Spring Security 구조는 유지한다.

- 테스트 버전에서는 모든 기능을 인증 없이 접근 가능하도록 permitAll 처리한다.

- Frontend Origin 중심 CORS 정책을 적용한다.

- SingleONE 내부 필터율은 고객-facing API/Frontend payload에 포함하지 않는다.

## 13.6 DB Migration 및 테스트

- MySQL: Flyway

- ClickHouse: versioned SQL migration

- Backend: JUnit 5, Mockito, Spring Boot Test, Testcontainers

- Frontend: Vitest, React Testing Library, Playwright

- Golden Dataset의 Index 결과는 자동 테스트로 고정한다.

# 14. 비기능 요구사항

## 14.1 정밀도

- SingleONE 계산은 BigDecimal 중심으로 수행하고 내부 계산값은 최소 소수점 6자리 정밀도를 유지한다.

- Index 점수와 SingleONE 구매는 화면에서만 반올림한다.

- Float/double의 누적 오차로 Golden Test가 달라지지 않도록 한다.

## 14.2 시간 기준

- Backend/DB Timestamp 저장 기준은 UTC

- timezone 정보가 없는 업로드 날짜/시간은 Asia/Seoul로 해석

- UI 표시는 Asia/Seoul

## 14.3 성능

- 대용량 객체 목록 서버 Pagination 기본 50 / 최대 200

- 검색/정렬은 Backend에서 수행

- Journey Flow 및 Top Path는 구매수 기준 Top 20 Path로 제한

- 파일 파싱은 Streaming

## 14.4 사용자 데이터 표현

- 원본 매체 성과는 항상 SingleONE 성과와 구분되어 확인 가능해야 한다.

- 내부 필터율 자체를 UI에 노출하지 않는다.

- Journey 이벤트 성과와 SingleONE 성과가 동일한 숫자라고 가정하지 않는다.

# 15. 테스트 데이터

## 15.1 Demo Dataset

- 메인 광고주: ABC Brand

- 기간: 90일

- 매체: Meta, TikTok, Google, Naver, Criteo 5개

- 각 매체: 캠페인 2개 → 캠페인당 광고 그룹 2개 → 광고 그룹당 광고 2개 수준의 계층 데이터

- 프로젝트: 5개 매체가 포함된 정상 비교 프로젝트 및 일부 캠페인 조합 프로젝트

- Journey: 다중 채널/단일 채널/반복 클릭/다중 구매가 섞인 synthetic event

## 15.2 Edge Test Brand

- Cost 1,000,000원 미만

- SingleONE 구매 10 미만

- 구매 0

- 필수값 누락

- 유효 비교 매체 1개

- 주간 예산 변동 부족

- Journey 이벤트 없음

- Simulation 모델 유효/무효/외삽 범위 케이스

## 15.3 Golden Index Dataset

| **매체** | **Cost** | **Impressions** | **Clicks** | **원본 구매** | **원본 매출** | **필터율** |
|----|----|----|----|----|----|----|
| Meta | 50,000,000 | 5,200,000 | 115,000 | 1,280 | 195,000,000 | 65% |
| TikTok | 35,000,000 | 6,000,000 | 105,000 | 720 | 102,000,000 | 62% |
| Google | 45,000,000 | 4,100,000 | 130,000 | 1,350 | 210,000,000 | 69% |
| Naver | 30,000,000 | 3,500,000 | 66,000 | 610 | 90,000,000 | 64% |
| Criteo | 20,000,000 | 2,200,000 | 35,000 | 300 | 45,000,000 | 61% |

| **매체** | **SingleONE 구매** | **SingleONE 매출** | **Expected Index(내부)** | **UI 표시** |
|----|----|----|----|----|
| Google | 931.5 | 144,900,000 | 133.525931 | 134 |
| Meta | 832.0 | 126,750,000 | 108.884222 | 109 |
| TikTok | 446.4 | 63,240,000 | 99.183914 | 99 |
| Naver | 390.4 | 57,600,000 | 90.429307 | 90 |
| Criteo | 183.0 | 27,450,000 | 67.976627 | 68 |
| 평균 |  |  | 100.000000 | 100 |

## 15.4 Golden Journey Dataset

| **사용자** | **여정**                          | **구매매출** |
|------------|-----------------------------------|--------------|
| U001       | Meta → Google → Purchase          | 100,000      |
| U002       | TikTok → Meta → Purchase          | 120,000      |
| U003       | Google → Purchase                 | 80,000       |
| U004       | Meta → Google → TikTok → Purchase | 150,000      |

| **채널** | **Expected 기여 구매** |
|----------|------------------------|
| Google   | 1.8333…                |
| Meta     | 1.3333…                |
| TikTok   | 0.8333…                |
| 합계     | 4.0000                 |

Expected Channel Pair: Meta+Google = 2개 여정, Meta+TikTok = 2개 여정, Google+TikTok = 1개 여정. 하나의 3채널 Journey가 여러 pair를 생성할 수 있으므로 pair count 합계가 구매수보다 클 수 있다.

# 16. Acceptance Criteria

아래 항목은 구현 완료 판정 기준이다. Golden 계산은 Backend 자동 테스트에 포함해야 한다.

| **ID** | **검증 항목** | **Acceptance Criteria** |
|----|----|----|
| AC-01 | Dashboard 기본 진입 | 광고주/프로젝트가 선택 가능한 상태에서 Dashboard를 열면 기본 기간은 최근 30일, 이전 기간 비교는 ON이다. |
| AC-02 | 5매체 Index Golden | Golden Dataset을 조회하면 내부 Index가 Google 133.525931, Meta 108.884222, TikTok 99.183914, Naver 90.429307, Criteo 67.976627이고 평균은 100이다. |
| AC-03 | UI Index 반올림 | AC-02 결과를 UI에서는 134/109/99/90/68로 표시한다. |
| AC-04 | 2개 유효 매체 | 유효 매체가 2개인 경우 두 매체 Index 평균도 100이어야 한다. |
| AC-05 | Cost 최소 조건 | 매체 cost가 1,000,000 KRW 미만이면 원본/SingleONE 성과는 보이지만 Index는 \`데이터 부족\`이다. |
| AC-06 | SingleONE 구매 최소 조건 | 내부 SingleONE purchases가 10 미만이면 화면 반올림값과 무관하게 Index는 \`데이터 부족\`이다. |
| AC-07 | 구매 0 | SingleONE purchases=0이면 CPA=\`-\`, ROAS=0%, Index 산출 제외다. |
| AC-08 | 필수 데이터 누락 | Index 필수 원본 필드가 누락되면 해당 매체는 \`필수 데이터 누락\`이며 weight redistribution 없이 전체 매체 score를 산출하지 않는다. |
| AC-09 | 유효 매체 부족 | 프로젝트/기간 내 유효 매체가 1개 이하이면 \`비교 가능한 매체 부족\`을 표시한다. |
| AC-10 | 운영일 | cost\>0인 distinct date가 6일이면 데이터 부족, 7일 이상이면 운영일 조건을 만족한다. |
| AC-11 | 계층 합산 | 광고 → 광고 그룹 → 캠페인 → 매체의 원본 및 SingleONE 성과 합계가 내부 정밀도에서 일치한다. |
| AC-12 | 하위 소규모 성과 | 캠페인/광고그룹/광고의 SingleONE 구매가 10 미만이어도 성과값은 숨기지 않는다. |
| AC-13 | Index 계층 제한 | 캠페인/광고 그룹/광고 화면에는 SingleONE Index 점수가 존재하지 않는다. |
| AC-14 | 이전 기간 | 동일 길이의 직전 기간을 사용하고 이전 기간 조건 부족 시 \`이전 기간 데이터 부족\`을 표시한다. |
| AC-15 | Rolling window | D 날짜 point는 D-6~D 데이터를 사용한다. 선택 기간 전 데이터가 존재하면 시작일 point 계산에도 사용할 수 있다. |
| AC-16 | Rolling 유효 매체 부족 | 특정 7일 window에서 유효 매체가 1개 이하이면 해당 point score를 표시하지 않는다. |
| AC-17 | 프로젝트 최소 매체 | 서로 다른 매체가 1개뿐인 프로젝트는 저장할 수 없다. |
| AC-18 | 프로젝트 캠페인 규칙 | 각 선택 매체에 최소 1개 campaign이 있어야 하며 동일 campaign을 동일 프로젝트에 중복 추가할 수 없다. |
| AC-19 | 프로젝트 간 캠페인 재사용 | 동일 campaign은 서로 다른 프로젝트에 포함될 수 있다. |
| AC-20 | 프로젝트 기간 미보유 | Project entity 자체에는 분석 기간이 저장되지 않는다. |
| AC-21 | 전체 캠페인 프로젝트 | 시스템 \`전체 캠페인\` 프로젝트는 수정/삭제할 수 없고 참고용으로 표시된다. |
| AC-22 | Simulation 프로젝트 제한 | \`전체 캠페인\` 프로젝트는 Media Planning Simulation에서 선택할 수 없다. |
| AC-23 | 상세 Route 상태 유지 | Dashboard에서 매체 상세로 이동했다가 돌아오면 광고주/프로젝트/기간/이전기간 비교 상태가 유지된다. |
| AC-24 | 상세 계층 탐색 | 매체→캠페인→광고그룹→광고를 별도 화면으로 탐색할 수 있고 Breadcrumb으로 상위 이동 가능하다. |
| AC-25 | SingleONE 인정률 비공개 | 고객 UI/API payload에 내부 필터율이 노출되지 않는다. SingleONE 구매 hover에는 “자체 내부 전환 기준입니다.”만 노출한다. |
| AC-26 | 성과 업로드 검증 실패 | 파일 내 오류가 한 건이라도 있으면 batch는 FAILED이고 어떤 행도 활성 분석 데이터가 되지 않는다. |
| AC-27 | 성과 중복키 | 성과 natural key는 date+advertiser_id+media+campaign_id+ad_group_id+ad_id다. |
| AC-28 | 기존 데이터 중복 확인 | 기존 SUCCESS key와 충돌하면 DUPLICATE_CONFIRMATION_REQUIRED가 되고 사용자 확인 전 IMPORTING으로 진행하지 않는다. |
| AC-29 | 중복 취소 | 사용자가 overwrite를 취소하면 batch는 CANCELLED가 된다. |
| AC-30 | SUCCESS 활성화 | SUCCESS가 아닌 upload_batch_id의 ClickHouse 데이터는 Dashboard/Index/Journey 분석에 사용되지 않는다. |
| AC-31 | XLSX worksheet | .xlsx 업로드는 첫 번째 worksheet만 읽는다. |
| AC-32 | 파일 제한 | 50MB 초과 또는 1,000,000행 초과 파일은 업로드 검증에서 거부된다. |
| AC-33 | Master Upsert | 성과 batch SUCCESS 후 Advertiser/Campaign/AdGroup/Ad master가 upsert되고 최신 date 이름이 표시된다. |
| AC-34 | Journey 7일 window | 구매 8일 전 click은 제외하고 7일 이내 click만 touchpoint로 인정한다. |
| AC-35 | Journey 연속 반복 | 연속 동일 channel clicks는 raw event에 남지만 path 표시에서는 하나로 압축한다. |
| AC-36 | Journey unique-channel Linear | Meta→Google→Meta→Purchase는 Attribution에서 Meta와 Google 각각 0.5 credit을 받는다. |
| AC-37 | 다중 구매 Journey | 직전 구매 이전 click은 다음 구매 Journey에 재사용되지 않는다. |
| AC-38 | 프로젝트 Journey | 선택 프로젝트에 포함되지 않은 campaign의 CLICK은 해당 Journey 분석에서 제외된다. |
| AC-39 | Journey Golden | Golden Journey에서 Google 1.8333…, Meta 1.3333…, TikTok 0.8333…, 합계 4.0의 기여 구매가 산출된다. |
| AC-40 | Channel Pair unordered | Meta+Google과 Google+Meta는 동일 pair로 집계한다. |
| AC-41 | Channel Pair count | 3채널 Journey는 3개의 pair를 만들 수 있어 pair count 합계가 구매수보다 커도 오류가 아니다. |
| AC-42 | Journey 명칭 | Journey 탭에서 \`SingleONE 기여 구매\`라는 표현을 사용하지 않는다. |
| AC-43 | Simulation 비추천성 | Simulation 어디에서도 증액/감액/최적 예산/구매 최대화/매출 최대화 문구 또는 자동 배분이 제공되지 않는다. |
| AC-44 | Simulation 총예산 | 사용자가 입력한 매체별 예산 합계가 총예산으로 자동 계산된다. |
| AC-45 | 환산 현재 운영 | 기준기간 cost를 일평균으로 환산해 시뮬레이션 기간 일수에 맞춘 참고 예산을 계산한다. |
| AC-46 | 주간 변환 | 시뮬레이션 매체 예산은 budget/days\*7로 모델에 입력되고 weekly prediction은 \*days/7로 기간 예측치로 환산된다. |
| AC-47 | 모델 최소 조건 | valid week\<6, SingleONE 구매합계\<100, max/min\<1.2, R²\<0.50, a\<=0 중 하나라도 해당하면 해당 모델은 무효다. |
| AC-48 | 두 모델 필요 | 구매 모델 또는 매출 모델 중 하나라도 무효이면 해당 매체 전체 결과는 \`예측 불가\`다. |
| AC-49 | 150% 초과 | weekly simulation budget이 historical max weekly cost의 150%를 초과하면 \`예측 불가\`다. |
| AC-50 | 허용 외삽 | historical max 초과~150% 이하 또는 0 초과 historical min 미만은 예측 가능하지만 신뢰도 \`낮음\`이다. |
| AC-51 | 0원 예산 | 매체 예산이 0원이면 예상 성과는 0이며 전체 KPI 산출을 방해하지 않는다. |
| AC-52 | 전체 KPI 산출 불가 | 예산이 0보다 큰 매체 중 하나라도 예측 불가이면 전체 예상 SingleONE 구매/매출/CPA/ROAS는 \`산출 불가\`다. |
| AC-53 | 시뮬레이션 비저장 | Simulation 상태는 Zustand 메모리에만 유지되며 refresh/revisit 시 초기화되고 DB/LocalStorage에 남지 않는다. |
| AC-54 | 목록 Pagination | Campaign/AdGroup/Ad 목록 API의 기본 page size는 50, 최대 200이며 검색/정렬은 서버가 수행한다. |
| AC-55 | Journey Top20 | Sankey와 Top Path는 구매수 기준 Top 20 path를 표시한다. |

# 17. 구현 제외/향후 확장

| **향후 후보** | **현재 처리** |
|----|----|
| 실제 매체 API 연결 | Test 이후 확장 |
| 실제 SingleONE 전환 판정 알고리즘 | Test 이후 고정 필터율 대체 |
| 로그인/조직/권한 | Test 이후 확장 |
| 실제 cross-platform identity stitching | Test 이후 확장 |
| 예산 실행/광고 플랫폼 write-back | 현재 제외 |
| 캠페인/광고그룹/광고 수준의 Index 점수 | 현재 제외. 공식 Index 비교 단위는 매체 |
| 매체 내부 자동 예산 최적화 | 현재 제외 |
| 시나리오 저장/비교 | 현재 제외 |
| 원본 데이터 행 단위 편집/삭제/rollback | 현재 제외 |

# 18. Claude Code 구현 체크리스트

## 18.1 구현 전 필수 원칙

- □ 과거 “비교 그룹” 용어를 코드/화면/API에 사용하지 않고 \`프로젝트/Project\`로 통일한다.

- □ \`group_id/group_name\` 대신 \`project_id/project_name\`을 사용한다.

- □ 저장 시나리오/Snapshot 관련 Entity, 화면, API를 만들지 않는다.

- □ Media Planning Simulation에 자동 추천·자동 배분·최적화 목표 로직을 만들지 않는다.

- □ 내부 필터율을 Frontend/API에 노출하지 않는다.

- □ SingleONE Index 점수는 매체 레벨에서만 산출한다.

- □ Journey 성과와 SingleONE 성과를 동일한 conversion pool로 합치지 않는다.

- □ Index 계산과 Golden Dataset을 Backend 자동 테스트로 고정한다.

- □ MySQL JPA와 ClickHouse 접근 경로를 분리한다.

- □ upload_batch_id가 SUCCESS인 데이터만 분석에 포함한다.

- □ 시간/정밀도/반올림 규칙을 공통 Utility로 구현하여 화면마다 다르게 계산하지 않는다.

- □ 모든 DTO/API 계약은 OpenAPI 문서와 일치하도록 유지한다.

## 18.2 권장 구현 순서

1\. DB migration 및 master/upload domain 구축

2\. 성과/Journey 업로드 검증·batch 상태·ClickHouse 적재

3\. SingleONE 필터/Index 계산 서비스 + Golden automated tests

4\. 프로젝트 CRUD 및 캠페인 selection

5\. Dashboard

6\. 매체/캠페인/광고 그룹/광고 상세

7\. Journey & Attribution

8\. Media Planning Simulation model 및 UI

9\. Playwright 핵심 E2E와 Edge Acceptance Criteria 완료

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr>
<th><strong>최종 구현 준비도</strong><br />
본 문서의 요구사항 기준으로 핵심 비즈니스 로직, 데이터 Grain, 계산식, 계층, 화면 범위, 오류 처리, 테스트 조건 및 기술 스택이 정의되어 있다. Claude Code는 본 PRD를 기준으로 테스트 제품을 구현하되, 명시적 제외 기능을 임의로 추가하거나 삭제된 과거 요구사항을 복원하지 않아야 한다.</th>
</tr>
</thead>
<tbody>
</tbody>
</table>
