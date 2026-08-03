# TunA Analytics Engine

Analytics Engine은 수집서버에서 수집된 지표(counter) 데이터를 이용하여 시스템 관리에 도움이 되는 정보를 제공합니다.  

* 하는 일: IQR 기반 이상치 제거, 평균·표준편차로 임계치 계산, 피어슨 상관계수 계산, ANOVA 기반 추천

## 기능

---

+ 동적임계치에 적합한 오브젝트 추천
+ 동적임계치 자동 설정
+ 상관계수 설정

## 핵심 로직: 병렬 데이터 확보 워크플로우

---

`main.py`에서 시작되는 세 작업(`run_corrcoef`, `run_dynamic_threshold`, `run_object_recommendation`)은 각각 상관계수 계산 / 동적임계치 계산 / 오브젝트 추천이라는 다른 목적을 가지지만, ElasticSearch에서 대량의 데이터를 가져오는 부분은 **완전히 동일한 패턴**을 공유합니다. 이 패턴이 본 프로젝트의 성능을 좌우하는 핵심 로직입니다.

### 전체 흐름

```
main()
 ├─ run_corrcoef()               ──┐
 ├─ run_dynamic_threshold()       ──┼─→  ①Config/API/DAO 초기화
 └─ run_object_recommendation()  ──┘      → ②TunaAsyncDao 병렬 데이터 확보 (아래 참고)
                                           → ③작업별 로컬 연산 (상관계수 / 이상치·임계치 / ANOVA 추천)
                                           → ④ElasticSearch 반영 + 완료 알림
```

세 작업 모두 로컬에 없는 (지표, 오브젝트) 조합만 추려서 `TunaAsyncDao.export_data_to_pickle()`을 호출한 뒤, 이후의 CPU 연산(pandas 통계 계산)은 로컬 pickle 파일만 읽어서 처리합니다. 즉 **"느린 ES 네트워크 조회"와 "반복적인 CPU 연산"을 분리**하는 것이 이 워크플로우의 목적입니다.

### `TunaAsyncDao`의 coroutine + semaphore 병렬 조회 (`src/dao/async_es.py`)

대량 데이터를 읽어오는 시간을 최소화하기 위해, 단순히 데이터를 하나씩 순차 조회하지 않고 **coroutine 기반 동시 처리**로 구성되어 있습니다.

```
export_data_to_pickle(need_repo_list)
  └─ main_coroutine()                         ← 동시 실행 개수 통제
       semaphore = min(CPU 코어 수 × 2, 8)      ← ES 서버 과부하 방지용 상한
       asyncio.gather(sub_coroutine × N개)      ← N개를 한꺼번에 스케줄링
            ├─ sub_coroutine #1 ─┐
            ├─ sub_coroutine #2 ─┤  세마포어가 동시에 최대 8개까지만 실행 허용
            ├─ ...               │  (나머지는 semaphore 대기)
            └─ sub_coroutine #N ─┘
                 각자: async_scan(size=5000 단위 스트리밍) → DataFrame 변환 → pickle 저장
```

```python
# src/dao/async_es.py

async def main_coroutine(self, need_repo_list: []):
    """메인 코루틴: 동시 실행할 서브 코루틴 개수를 통제하고 서브 코루틴을 실행한다"""

    # semaphore 객체를 생성해서 최대 동시 실행 가능한 sub coroutine 개수를 제한한다.
    semaphore = asyncio.Semaphore(min(multiprocessing.cpu_count() * 2, 8))

    sub_coroutines = []
    for source in need_repo_list:
        sub_coroutines.append(
            self.sub_coroutine(
                semaphore=semaphore,
                search_datetime=source["datetime"],
                counter=source["counter"],
                objHash=source["objHash"],
            )
        )

    # asyncio.gather() 함수를 사용해서 sub coroutine을 실행하고 결과를 가져온다.
    await asyncio.gather(*sub_coroutines)

async def sub_coroutine(self, semaphore, search_datetime, counter, objHash):
    """서브 코루틴: 직접 ElasticSearch에서 데이터를 읽어와서 파일을 남기는 주체이다"""

    async with semaphore:
        index_name = "counter-metric-"
        if self._config.get_use_5m():
            index_name = "counter-5m-metric-"

        from_datetime = datetime_util.get_truncate_date(search_datetime)
        to_datetime = from_datetime + timedelta(days=1) - timedelta(microseconds=1)
        yyyymmdd_list = datetime_util.get_utc_yyyymmdd_between_datetimes(
            from_datetime, to_datetime
        )
        indexes = [index_name + yyyymmdd for yyyymmdd in yyyymmdd_list]

        response_list = []
        from_epoch_millis = datetime_util.to_epoch_millis(from_datetime)
        to_epoch_millis = datetime_util.to_epoch_millis(to_datetime)
        async for doc in helpers.async_scan(
            client=self._client,
            index=indexes,
            query={
                "query": {
                    "bool": {
                        "filter": [
                            {"term": {"counter": counter}},
                            {"term": {"objHash": objHash}},
                            {
                                "range": {
                                    "ctime": {
                                        "gte": from_epoch_millis,
                                        "lte": to_epoch_millis,
                                    }
                                }
                            },
                        ]
                    }
                }
            },
            ignore_unavailable="true",
            docvalue_fields=["ctime", "value"],
            size=5000,
        ):
            response_list.append(
                {"ctime": doc["fields"]["ctime"][0], "value": doc["fields"]["value"][0]}
            )

        # 데이터저장소에서 받아온 데이터를 DataFrame으로 변환 후 pickle 파일로 저장
        df = pd.DataFrame(response_list)
        if not df.empty:
            df = df.sort_values(by="ctime")
            df["ctime"] = pd.to_datetime(df["ctime"], utc=True).dt.tz_convert(
                self._config.get_timezone_name()
            )
        pickle_name = "{}_{}".format(counter, objHash)
        pickle_controller.save_pickle(
            self._config.get_pickle_dir(), search_datetime, pickle_name, df
        )
```

- **왜 코루틴인가**: (지표, 오브젝트) 조합별 ES 조회는 서로 독립적인 I/O 대기 작업이라, 순차 처리하면 네트워크 왕복 시간이 그대로 누적됩니다.
- **왜 semaphore로 상한을 두는가**: 무제한 동시 요청은 ES 서버에 부하를 줘 타임아웃/성능 저하를 유발할 수 있어, "동시에 최대 8개"로 제한해 처리 시간 최소화와 서버 부하 방지를 함께 만족시킵니다.
- **관련 소스**: `src/dao/async_es.py` (`TunaAsyncDao`), `src/corrcoef/__init__.py`·`src/threshold/__init__.py`·`src/recommendation/__init__.py`의 각 `run()` 메서드에서 이 패턴을 동일하게 호출합니다.

## 동적임계치 계산 로직 (`src/threshold/__init__.py`)

---

지표(counter)별로 "정상 범위"를 통계적으로 계산해서 상/하한 임계치를 자동 산출합니다.

1. **대상 선정**: API 서버의 임계치 설정(`get_threshold_config()`)에서 지표(counter) × 오브젝트(objHash) 조합을 추리되, 최근 2일 내 확인된 오브젝트만 대상으로 합니다.
2. **데이터 확보**: 공용 워크플로우(`TunaAsyncDao.export_data_to_pickle()`)로 최근 `search_days`(기본 14일)치 데이터를 병렬 조회해 로컬 pickle에 저장합니다.
3. **적용단위(au_type) 결정**: `AU1`(요일별) 또는 `AU2`(주중/주말별) — 이후 시간대 그룹핑 기준이 됩니다.
4. **이상치 제거**: 시간대(`hh`) × 적용단위(`au_value`)로 그룹핑한 뒤 **IQR(사분범위) 방식**으로 그룹 내 이상치를 제거합니다. (`Q1 - 1.5×IQR` ~ `Q3 + 1.5×IQR` 범위를 벗어나면 이상치)

   ```python
   # src/threshold/__init__.py - DynamicThreshold._find_outliers_iqr()

   def _find_outliers_iqr(self, values: SeriesGroupBy):
       Q1 = values.quantile(0.25)  # 1사분위수
       Q3 = values.quantile(0.75)  # 3사분위수
       IQR = Q3 - Q1  # 사분범위(IQR, InterQuartile Range)
       lower_bound = Q1 - (1.5 * IQR)
       upper_bound = Q3 + (1.5 * IQR)
       return (values < lower_bound) | (values > upper_bound)

   # 적용 위치 - run()
   # au_value와 hh 컬럼을 기준으로 그룹화하고, 그룹별 value 컬럼에 대해서 이상치 데이터는 제외함
   df = df[
       ~df.groupby(["au_value", "hh"], group_keys=False)["value"].apply(
           self._find_outliers_iqr
       )
   ]
   ```

5. **time_window별 평균/표준편차 계산**: 예를 들어 "월요일 13:10~13:20"의 임계치를 구할 때, 설정된 `time_window`(분단위) 기준으로 앞뒤에 여유(`TIME_WINDOW_MINUTES`)를 두고 여러 주의 동일 시간대 데이터를 모아 평균·표준편차를 계산합니다.
6. **임계치 계산**:
   ```
   상한 = 평균 + (표준편차 × sigma_level)
   하한 = 평균 - (표준편차 × sigma_level)
   ```
   `sigma_level`(3 또는 6)과 `direction`(UPPER/LOWER/UPPER_LOWER 중 계산할 방향)은 설정값을 따르며, 표준편차가 0이면 계산을 건너뜁니다.

   ```python
   # src/threshold/__init__.py - DynamicThreshold.run() (time_window_group 별 루프)

   for key, datetime_range_list in time_window_groups.items():
       au_value = key[0]
       range_name = key[1]
       # 데이터 계산 기간은 기본 time_window 기간에 앞뒤로 TIME_WINDOW_MINUTES(분단위)만큼 범위를 넒힌 기간이다
       filtered_df = pd.DataFrame(columns=df.columns)
       for datetime_range in datetime_range_list:
           adj_from_datetime = datetime_range["from_datetime"] - timedelta(
               minutes=self.TIME_WINDOW_MINUTES
           )
           adj_to_datetime = datetime_range["to_datetime"] + timedelta(
               minutes=self.TIME_WINDOW_MINUTES
           )
           tmp_df = df[
               (df["ctime"] >= adj_from_datetime) & (df["ctime"] < adj_to_datetime)
           ]
           filtered_df = pd.concat([filtered_df, tmp_df])

       if filtered_df.empty:
           continue

       # 평균과 표준편차 구하기
       mean_value = filtered_df["value"].mean()
       std_value = filtered_df["value"].std()

       # 평균이나 표준편차가 숫자가 아니거나 표준편차가 0이면 동적임계치 계산이 필요하지 않으므로, 다음 time_window로 진행
       if math.isnan(mean_value) | math.isnan(std_value) | (std_value == 0.0):
           continue

       # 동적임계치 계산
       upper_threshold = 0.0
       lower_threshold = 0.0
       if limit_bound_name in ["UPPER_LOWER", "UPPER"]:
           upper_threshold = mean_value + (std_value * sigma_level)
       if limit_bound_name in ["UPPER_LOWER", "LOWER"]:
           lower_threshold = mean_value - (std_value * sigma_level)
   ```

7. **저장 및 알림**: 결과를 ES(`aiops-dynamic-threshold` 인덱스)에 upsert → 오래된 데이터 삭제 → 인덱스 백업 → API 서버에 완료 알림.

## 상관계수 계산 로직 (`src/corrcoef/__init__.py`)

---

같은 오브젝트 내에서 함께 감시할 만한 "상관성 있는 지표쌍"을 자동으로 찾아냅니다.

1. **대상 선정**: API 서버의 상관계수 설정(`get_correlation_config()`)에서 지표(counter) 목록 × 오브젝트(objHash) 목록을 가져옵니다.
2. **데이터 확보**: 공용 워크플로우로 대상 데이터를 병렬 조회해 로컬 pickle에 저장합니다.
3. **지표쌍 조합**: `itertools.combinations`로 지표를 2개씩 묶은 모든 조합에 대해 반복합니다.
4. **전처리**: 두 지표를 시간(`ctime`) 기준으로 merge하고, 둘 다 값이 0인 시각은 제외합니다.
5. **일별·시간대별 상관계수 계산**: `date` + `hh`로 그룹핑 후 피어슨 상관계수를 산출합니다.
   ```
   corr(i, j) = cov(i, j) / (stdev(i) × stdev(j))
   ```
   표준편차가 0이 되는 등 계산이 불가능한 경우(NaN)는 0으로 처리합니다.

   ```python
   # src/corrcoef/__init__.py - CorrCoef.run()

   # 컬럼 추가 - 날짜, 시간대
   df["date"] = df["ctime"].apply(
       lambda ctime: ctime.replace(hour=0, minute=0, second=0, microsecond=0)
   )
   df["hh"] = df["ctime"].dt.strftime("%H")  # 시간대

   # 연월일과 시간대, 두 개 컬럼으로 그룹핑하고 값(value_x와 value_y)을 이용해서 상관계수 계산하기
   corr_df = (
       df.copy(deep=True)
       .groupby(["date", "hh"], group_keys=False)
       .apply(lambda g: g["value_x"].corr(g["value_y"]))
       .reset_index()
       .rename(columns={0: "corrcoef"})
   )
   corr_df.rename(columns={corr_df.columns[2]: "corrcoef"}, inplace=True)
   corr_df = corr_df.fillna(0)
   ```

6. **1차 유의성 필터**: 날짜별로 "상관계수 절댓값이 `correlation_coefficient_least`(기본 0.6) 이상인 시간대 개수"를 구하고, 이 개수의 일평균이 `correlation_hours`(기본 10시간) 미만이면 해당 지표쌍은 폐기합니다.

   ```python
   # 1차 필터링: 두 지표(counter) 간에
   # 상관계수가 설정값(기본 0.6) 이상인 시간대가 일평균 설정값(기본 10회) 미만이면 다음 대상으로 진행
   if (
       corr_df.groupby("date")["corrcoef"]
       .apply(
           lambda x: (abs(x) >= self._config.get_correlation_coefficient_least()).sum()
       )
       .mean()
       < self._config.get_correlation_hours()
   ):
       continue
   ```

7. **적용단위별 평균 집계**: 통과한 지표쌍에 요일별(AU1) 또는 주중/주말별(AU2) `au_value`를 추가하고, `(au_value, hh)`로 다시 그룹핑해 상관계수 평균을 계산합니다.
8. **2차 필터**: 평균 상관계수가 `correlation_coefficient_least`(기본 0.6)를 초과하는 시간대만 최종 결과로 남깁니다. 이 시간대가 "두 지표가 함께 감시되어야 할 시간대"가 됩니다.

   ```python
   # 적용단위(au) 값과 시간대, 두 개 컬럼으로 그룹핑하고
   # 상관계수(corrcoef) 컬럼의 평균값을 계산한 DataFrame을 작성한다
   mean_df = corr_df.groupby(["au_value", "hh"])["corrcoef"].mean().reset_index()

   # 상관계수가 설정값(기본 0.6) 이상인 시간대만 추출
   mean_df = mean_df[
       mean_df["corrcoef"] > self._config.get_correlation_coefficient_least()
   ]
   ```

9. **저장 및 알림**: 결과를 ES(`aiops-correlation-counter-set` 인덱스)에 upsert → 오래된 데이터 삭제 → 인덱스 백업 → API 서버에 완료 알림.

> 두 로직 모두 저장 방식(upsert → 오래된 데이터 삭제 → 백업 → 완료 알림)이 동일한 패턴을 따릅니다.

## 파일 설명

---

| 기본 경로                        | 설명                                         |
|:-----------------------------|:-------------------------------------------|
| ${TUNA_AI_PATH}              | 주 디렉토리                                     |
| ${TUNA_AI_PATH}/conf/t2.conf | Config 파일                                  |
| ${TUNA_AI_PATH}/logs         | 프로그램 실행 로그가 저장되는 경로                        |
| ${TUNA_AI_PATH}/pickle_db    | 프로그램에 필요한 가공된 임시 데이터가 파일로 저장되는 로그가 저장되는 경로 |

## 시작과 종료

---

### 시작

```shell
$ start.sh
```

### 종료

```shell
$ stop.sh
```

## t2.conf

---

TunA AI는 config 파일(./conf/t2.conf)에 설정된 구성을 사용합니다. 

| Section       | Option                        | 설명                              | 기본값         |
|:--------------|:------------------------------|:--------------------------------|:------------|
| AI_Config     | correlation_coefficient_least | 상관관계를 계산할 때에 충족되어야 하는 상관계수의 최소값 | 0.6         |
| AI_Config     | correlation_hours             | 상관계수 최소값을 충족하는 일 평균 시간          | 10          |
| Schedule      | local_batch_time              | 매일 작업이 실행될 시각                   | 09:00       |
| API_Server    | api_server                    | TunA 수집서버 IP                    | localhost   |
| API_Server    | api_port                      | TunA 수집서버 Port                  | 6180        |
| ElasticSearch | es_server                     | ElasticSearch IP                | localhost   |
| ElasticSearch | es_port                       | ElasticSearch Port              | 9200        |
| Log           | log_dir                       | 로그를 남길 경로                       | ./logs      |
| Log           | log_size                      | 로그 파일 사이즈                       | 10 MB       |
| Log           | log_retention                 | 로그 파일 개수                        | 10          |
| Log           | log_level                     | 로그 레벨                           | WARN        |
| Data_Dump     | search_days                   | 데이터 검색 기간(일단위)                  | 14          |
| Data_Dump     | pickle_dir                    | 데이터를 덤프받을 경로              | ./pickle_db |


## Python Project 개발 환경 구성

---

Python 3.9.x, Windows 64bit(amd64), PyCharm 환경에서 개발되었습니다.

```shell
$ git clone https://.../tuna.ai.git
$ pip install -r requirements.txt
```

PyCharm에서 프로젝트를 열면 venv가 자동으로 구성되며, `src` 폴더는 Sources Root로 지정해야 합니다.

배포용 단독 실행 파일은 프로젝트 루트에서 아래 명령으로 생성합니다. (pyinstaller는 cross compile을 지원하지 않으므로 배포할 운영체제별로 빌드해야 합니다.)

```shell
$ pyinstaller main.spec
```
