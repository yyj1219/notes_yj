# ZLGZ20 (Dynamic Query) 패키지 설명

본 문서는 SAP S/4HANA 시스템 내에서 사용할 수 있는 `ZLGZ20 (Dynamic Query)` 를 설명하기 별도 자료입니다.

---

## 요약

`ZLGZ20`은 Dynamic Query 작성과 실행을 위한 독자 프레임워크 입니다.

이 패키지는 개발자가 조회 SQL 또는 Query 메타데이터를 정의하고, 이를 저장/실행/노출 할 수 있도록 다음 요소를 갖추고 있습니다.

- Query 정의 저장 구조
- SQL 파싱 및 검증 유틸리티
- Query 실행 및 결과 반환 구조
- RAP Query Provider 기반의 서비스 노출 구조
- 실행 이력 관리 구조

`ZLGZ20`은 `조회형`, `집계형`, `다운로드형` 리포트 개발을 빠르게 수행하기 위한 실무형 프레임워크로 볼 수 있습니다.

다만 이 프레임워크는 `SAC`, `Analysis for Office`, `다차원 분석`, `하이어라키`, `예외 집계` 등 SAP 표준 분석 모델을 대체하기 위한 기술이라기보다, `직접 제어 가능한 Query 기반 리포트 프레임워크`에 가깝습니다.

---

## 1. ZLGZ20의 기본 개념

`ZLGZ20`은 개발자가 조회 로직을 코드에 직접 하드코딩하는 대신, Query 정의를 메타데이터와 SQL 중심으로 관리할 수 있도록 만든 패키지입니다.

쉽게 말해, 다음과 같은 흐름을 제공하는 구조입니다.

1. Query를 식별할 수 있는 코드와 속성을 정의한다.
2. 조회 SQL 을 저장한다.
3. 컬럼, 파라미터, 관계, 상수 등 실행에 필요한 메타정보를 함께 관리한다.
4. 런타임에 이를 조합하여 실행 가능한 SQL로 변환한다.
5. RAP Query Provider 또는 서비스 계층을 통해 결과를 소비한다.
6. 실행 이력을 남겨 운영 추적이 가능하도록 한다.

---

## 2. 주요 구성

### 2.1 주요 클래스

| 클래스명 | 역할 | 설명 |
|---|---|---|
| `ZCL_LGZ_DYNAMIC_QUERY_UTIL` | 공통 실행 유틸리티 | SQL 포맷팅, 파싱, Placeholder 처리, 실행용 SQL 생성, SQL 실행, Count 실행, 메타데이터 추출 등 프레임워크의 중심 유틸리티 역할 |
| `ZCL_LGZ_SQL_STATEMENT` | Query 정의 관리 | Query Statement 저장/로드, 검증, 복사/이동/삭제, 컬럼/파라미터 생성 등 설계 시점 메타데이터 관리 역할 |
| `ZCL_LGZ_SQL_COLUMN` | 컬럼 메타데이터 관리 | 조회 결과 컬럼의 속성 및 구성 정보를 관리하는 역할 |
| `ZCL_LGZ_SQL_DATA` | 실행 데이터 처리 | 실행 가능한 SQL 생성과 결과 데이터 반환을 담당하는 역할 |
| `ZCL_LGZ_SQL_METADATA` | 메타데이터 처리 | SQL 결과 구조나 컬럼 메타정보를 다루는 역할 |
| `ZCL_LGZ_SQL_PARAM` | 파라미터 관리 | 입력 파라미터 정의, 바인딩, 검증 등을 담당하는 역할 |
| `ZCL_LGZ_SQL_PARSE` | SQL 파서 | SQL 문장 분석 및 토큰/구문 해석 역할 |
| `ZCL_LGZ_QUERY_00020` | RAP Query Provider | `IF_RAP_QUERY_PROVIDER` 구현체로서 Filter/Paging 해석, SQL 실행, 결과 반환 역할 수행 |

### 2.2 주요 테이블

| 테이블명 | 설명 | 역할 |
|---|---|---|
| `ZLGZT2010` | Dynamic Query Statement | Query 기본정보와 SQL Statement를 저장하는 중심 헤더 테이블 |
| `ZLGZT2020` | Dynamic Query Column | 조회 결과 컬럼 정의를 저장하는 테이블 |
| `ZLGZT2030` | Dynamic Query Parameter | 입력 파라미터 정의를 저장하는 테이블 |
| `ZLGZT2040` | Dynamic Query Relation Definition | DQ 리포트간 관계 정의를 저장하는 테이블 |
| `ZLGZT2050` | Dynamic Query Constants | 상수값 또는 Placeholder 관련 정의를 저장하는 테이블 |
| `ZLGZT2060` | Dynamic Query Execution History | Query 실행 이력, 실행시간, 결과 건수 등을 기록하는 이력 테이블 |
| `ZLGZT2070` | Dynamic Query Workload Class | 실행 workload 또는 실행 클래스 관련 설정을 저장하는 테이블 |
| `ZLGZT2080` | Dynamic Query Relation Condition | 관계 조건 또는 연관 조건 정의를 저장하는 테이블 |

### 2.3 서비스 및 노출 계층

- RAP Query Provider 클래스
  - 예: `ZCL_LGZ_QUERY_00020`, `ZCL_LGZ_QUERY_00021`
- Custom Entity
  - 예: `ZLGZ_U_00020`, `ZLGZ_U_00021`
- Service Definition / Service Binding 계열 오브젝트
  - 예: `ZLGZ_SRV_20200_01`, `ZLGZ_SRV_20250_01`, `ZLGZ_SRV_U_00020_01`, `ZLGZ_SRV_U_00021_01`
  - 관련 Binding 예: `ZLGZ_SRV_20200_01_UI_20`, `ZLGZ_SRV_20250_01_UI_20`, `ZLGZ_SRV_U_00020_01_UI_V2`, `ZLGZ_SRV_U_00021_01_UI_V2`
- OData 관련 오브젝트
  - 예: `IWMO`, `IWSV`, `IWVB` 계열 오브젝트 다수 확인
  - 예시 이름: `ZLGZ_SRV_20200_01_UI_20`, `ZLGZ_SRV_20250_01_UI_20`, `ZLGZ_SRV_U_00020_01_API_V2`
 
`ZLGZ20`이 백엔드 내부 유틸리티로만 끝나는 것이 아니라, Query 실행 결과를 `화면`, `앱`, `외부 호출`에서 사용할 수 있는 형태로 노출하는 구조까지 고려하여 설계되었습니다.

---

## 3. ZLGZ20의 동작 방식

`ZLGZ20`의 기본 동작은 다음과 같이 이해할 수 있습니다.

```
[개발자/운영자]
    |
[Query 정의 저장]
    - Query 코드
    - SQL 문장
    - 컬럼
    - 파라미터
    - 관계/상수
    |
[ZLGZ20 유틸리티]
    - SQL 파싱
    - SQL 검증
    - Placeholder 처리
    - 실행용 SQL 생성
    |
[RAP Query Provider]
    - 요청 Filter/Paging 해석
    - SQL 실행
    - 결과 반환
    |
[Fiori / 서비스 소비]
```

즉, `ZLGZ20`은 `메타데이터 기반 Query 실행 엔진`이라고 설명할 수 있다.

---

## 4. 프로젝트에서 기대할 수 있는 장점

### 4.1 개발 생산성

프로젝트의 주력 인력이 복잡한 CDS 계층 설계보다 SQL 중심 접근이 훨씬 익숙하다면, 다음과 같은 점에서 생산성에 유리할 수 있습니다.

- SQL 기반 사고방식 유지 가능
- Query 정의를 프레임워크 안에서 재사용 가능
- 반복적인 조회성 리포트를 빠르게 양산 가능
- 공통 패턴을 메타데이터로 관리 가능

### 4.2 성능 통제

유사한 타 프로젝트(LGE NERP)는 계층형 CDS 사용으로 인한 성능 저하를 많이 경험했습니다. `ZLGZ20`은 SQL 제어권이 개발자에게 더 많이 있어서 다음 측면에서 유리합니다.

- 실행 SQL을 직접 이해하고 통제하기 쉽다.
- 성능 문제 진단이 직관적이다.
- 대량 데이터 조회에 대한 튜닝 여지가 크다.
- Query별로 개별 최적화가 가능하다.

### 4.3 프로젝트 현실 적합성

`ZLGZ20`은 `CDS Analytical Projection Views`보다 현실적인 대안이 될 수 있습니다.

- 신규 학습 부담이 상대적으로 낮다.
- 사용할 수 있는 패키지 소스가 존재하는 자산이다.
- 성능 예측 가능성이 더 높다.

---

## 5. ZLGZ20의 한계와 주의점

`ZLGZ20`이 유용한 대안이라 하더라도, 다음과 같은 한계가 있습니다.

### 5.1 Clean Core 관점

`ZLGZ20`은 SAP 표준 분석 모델이 아니라 커스텀 프레임워크이므로, Clean Core 관점에서는 `CDS Analytical Projection Views`보다 불리합니다.

### 5.2 표준 분석 도구 연계

`ZLGZ20`은 SAC, Analysis for Office, Embedded Analytics와 같은 표준 분석 소비 모델을 자연스럽게 제공하는 기술은 아닙니다.

### 5.3 운영 통제 필요

Dynamic Query는 강력하지만, 잘못 사용하면 위험할 수 있습니다.

예를 들어 다음 리스크가 있다.

- 과도한 범위 조회
- 비효율 SQL 등록
- 권한 통제 미흡
- 동시 실행에 따른 시스템 부하

따라서 다음 운영 가드레일이 필요하다.

- 필수 선택조건 정의
- 최대 조회 범위 제한
- 대량 조회 시 백그라운드 전환 기준
- Query 등록/변경 승인 절차
- 실행 이력 및 성능 모니터링
