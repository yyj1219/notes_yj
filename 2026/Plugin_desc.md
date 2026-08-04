## 프로젝트 개요

이 저장소는 독립적인 **TunA Server** (Scouter 기반 APM) 플러그인 모듈들의 모음입니다. **루트 aggregator pom이 없으며**, `tuna.server.plugin.*` 디렉터리 각각이 독립된 Maven 프로젝트로 개별 빌드/실행됩니다. TunA Server 본체는 sibling 저장소인 `C:\workspace\tuna.server`에 존재해야 합니다.

모듈 목록:
- `tuna.server.plugin.template` — 최소 구성의 참조용 모듈 (pom.xml만 존재, 소스 없음). 다른 모든 모듈이 그대로 따라 쓰는 기본 의존성 패턴을 보여줌.
- `tuna.server.plugin.slack` — Alert → Slack (Block Kit) 웹훅 전송.
- `tuna.server.plugin.teams` — Alert → Microsoft Teams adaptive card 웹훅 전송.
- `tuna.server.plugin.lgcns.next` — Teams 플러그인을 "NEXT" 플랫폼용으로 재구성한 거의 동일한 모듈 (adaptive card 클래스가 `adaptive`가 아닌 `cards/adaptive` 서브패키지에 위치); 채널 설정은 `plugin.next.yaml` / `plugin_teams.yaml`에 있음.
- `tuna.server.plugin.db` — Alert → DB insert.
- `tuna.server.plugin.lgcns.itsm` — Alert → HTTP API를 통한 ITSM 티켓 생성.
- `tuna.server.plugin.lgcns.api.client` — 범용 REST Alert 클라이언트.
- `tuna.server.plugin.cloud.alert` — 클라우드 메트릭 모니터링 및 임계치 기반 Alert *생성* (가장 최근/활발하게 개발 중인 모듈; 아래 아키텍처 섹션 참고).

패키지 컨벤션: `scouter.plugin.server.<플러그인명>`.

## 빌드 & 테스트

모든 작업은 모듈 단위로 진행합니다 — 항상 해당 플러그인 디렉터리 안에서 Maven을 실행하세요. 예:

```
cd tuna.server.plugin.slack
mvn clean package install -DskipTests
```

주의할 점:
- 모든 모듈이 `tuna-scouter-server`를 `system` 스코프 의존성으로 선언하며, 경로가 `C:/workspace/tuna.server/target/tuna-scouter-server-2.9.0.jar`로 하드코딩되어 있습니다. sibling `tuna.server` 저장소가 정확히 해당 버전으로 로컬에 빌드되어 있어야 하며, 그렇지 않으면 컴파일이 실패합니다. `tuna-scouter-common`은 반대로 사내 Nexus에서 받아옵니다 (`provided` 스코프).
- 사내 Nexus 저장소(`maven-releases` / `maven-snapshots`)는 `http://개발서버:8081/repository/...`에 호스팅되어 있으며, `tuna-scouter-common` 의존성 해석에 필요합니다.
- 최소한 `tuna.server.plugin.slack`과 `tuna.server.plugin.teams`/`tuna.server.plugin.lgcns.next`는 `maven-surefire-plugin`에 `<skipTests>true</skipTests>`가 기본으로 설정되어 있어, `mvn package`/`install`을 실행해도 테스트가 조용히 스킵됩니다. 실제로 테스트를 실행하려면:
  ```
  mvn test -DskipTests=false
  mvn test -DskipTests=false -Dtest=SlackChannelTest
  mvn test -DskipTests=false -Dtest=SlackChannelTest#specificMethod
  ```
- 패키징에는 (slack 등 일부 모듈에서) `maven-assembly-plugin`(`jar-with-dependencies`)을 사용해 `target/` 아래에 단일 fat jar를 생성합니다.
- 테스트는 JUnit Jupiter + AssertJ를 사용합니다.

### TunA Server와 연동한 실행/디버깅 (IntelliJ)

`tuna.server.plugin.cloud.alert/README.md`에 문서화되어 있지만 다른 플러그인에도 동일하게 적용됩니다:
1. 플러그인별 Maven 실행 설정 — Working directory를 해당 플러그인 폴더로, Command line은 `clean package install -DskipTests`로 지정.
2. "Remote Debug" 실행 설정 (host `localhost`, port `5005`).
3. TunA Server 실행 설정: Main class `tuna.server.Main`, VM options `-Dt2.home=C:\workspace\tuna.server\profile\local -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005`.
TunA Server 설정을 실행하면 설치된 모든 플러그인이 `BuiltInPluginManager.loadPlugins()`를 통해 함께 로드됩니다. 이후 원격 디버거를 붙이면 플러그인 코드의 브레이크포인트에 걸립니다.

## Alert 파이프라인 아키텍처 (slack/teams/next/db/itsm 공통)

- Scouter 코어가 `AlertPack`을 발행하면, 각 플러그인은 `@ServerPlugin(PluginConstants.PLUGIN_SERVER_ALERT) process(AlertPack)`으로 핸들러를 등록해 이를 수신합니다.
- `process()`는 가드 클로즈(guard clause) 필터 체인입니다 (`enable` → `sendAlert` → `skipAlertSet`/`skipFatalAlertSet` → 레벨 체크). 어느 하나라도 걸리면 즉시 리턴하여 Alert가 폐기됩니다. 이 체인을 통과한 것만 내부 producer/consumer 큐에 적재됩니다.
- 별도의 데몬 스레드가 이 큐를 소비하며, Alert를 채널별 메시지 VO로 변환한 뒤 싱글톤 `AlertSender` 데몬 스레드로 넘깁니다. 실제 HTTP POST 전송은 이 스레드가 설정된 웹훅별로 수행합니다.
- 채널/웹훅 설정은 `PluginConfigure`/`PluginConfigItem`이 `src/main/resources/conf` 아래의 YAML(`plugin_slack.yaml`, `plugin_teams.yaml`, `plugin.next.yaml`) 또는 `.conf` 파일에서 로드합니다. 채널은 `serviceGroup`으로 키가 매핑되며, 하나의 Alert가 콤마로 구분된 여러 `serviceGroup`을 가지면 여러 웹훅으로 팬아웃됩니다.
- 전송 경로에는 재시도 로직이 전혀 없습니다. HTTP 실패, non-2xx 응답, Slack의 "HTTP 200이지만 바디에 429가 포함된" 케이스 모두 로그만 남기고 해당 메시지는 그대로 폐기됩니다.

## Cloud Alert Plugin (`tuna.server.plugin.cloud.alert`)

이 모듈은 "판정 및 전달"만 담당하며 직접 Alert를 전송하지 않습니다:
- `AlertPack`에 `objType = "cloud"`와 `tags.put("alertType", AlertTypeConstants.CLOUD_ALERT)`를 채운 뒤 `AlertCore.add()`를 호출해, Slack/Teams 플러그인이 소비하는 것과 동일한 공유 파이프라인에 주입합니다.
- 하위(전송) 플러그인들은 `alertType == CLOUD_ALERT` 여부로 분기하여 호스트 에이전트 조회(`ObjectPack`/`ChartUtil`/`SQLUtil`)를 전부 건너뛰고, 고정된 `objName = "CLOUD"`, `serviceGroup = "DEFAULT"` 경로로 라우팅합니다.

10초 주기로 동작하는 독립적인 데몬 스레드 두 개:
- `CloudAlertRuleScheduler` — OpenSearch `cloud-alert-rule` 인덱스(`activeYn=Y`)를 다시 읽어 `alertRuleCache`(`ConcurrentHashMap`)를 갱신합니다. 갱신은 `clear()` 후 `putAll()` 순서로 이루어져 **원자적(atomic)이지 않습니다** — 동시에 읽는 스레드가 순간적으로 빈 캐시 또는 절반만 채워진 캐시를 볼 수 있습니다.
- `CloudAlertPlugin` — `process(CloudMetricPack)`은 의도적으로 아무 동작도 하지 않습니다(클라우드 메트릭은 지연 반영될 수 있기 때문). 실제 처리는 `run()`에서 이루어지며, `cloud-metric-data-*` 인덱스에서 `metricMetaId`/`resourceId`별 최신 값을 다시 조회한 뒤, `cloud-alert-latest` 인덱스에 기록된 값보다 최신인 데이터만 걸러냅니다 (이미 처리한 데이터의 중복 검사 방지).

임계치/조건 평가 관련 참고:
- 조건은 fatal이 먼저 오도록 정렬됩니다(`sortConditions`). 첫 번째로 매칭된 조건이 확정되며 나머지 조건 평가는 건너뛰므로, 평가 순서가 결과에 직접 영향을 줍니다.
- 리소스 태그 매칭(`filterBy`/`isResourceMatched`): 필터가 비어 있으면 전체가 매칭됩니다. 필터 태그 값이 배열이면 리소스 태그 값과 OR 조건으로 비교하고, 스칼라 값이면 완전 일치를 요구합니다. 리소스에 필터 태그가 없으면 즉시 불일치로 처리됩니다.

## 작업 이력 정리 (Slack / Cloud Alert 개발)

- 기간: 2024-01-01 ~ 2024-11-25
- 커밋 수: 44개

작업은 크게 3가지로 구분된다.

1. Slack Plugin 개발
2. Cloud Alert Plugin 개발
3. Slack ↔ Cloud Alert 연동

---

### 1. Slack Plugin 개발

기간: 2024-01-01 ~ 2024-11-12

- Slack 플러그인 로직 최초 개발
- 테스트 환경 구성 (Java 17, Scala 버전 불일치 이슈 해결), Teams 플러그인 테스트 코드 추가
- 직접 만든 `SlackMessage` 클래스를 제거하고 공식 Slack API Client(1.36.1) 라이브러리로 교체
- snakeyaml 의존성 버전 변경 대응, 중복 의존성 제거, `tuna.scouter` 의존성 2.8.0 반영

#### 핵심 클래스 및 로직 흐름

3단 큐(Producer-Consumer) 파이프라인 구조.

- **[AlertToSlack.java](tuna.server.plugin.slack/src/main/java/scouter/plugin/server/slack/AlertToSlack.java)**
  - `process(AlertPack)` — `@ServerPlugin`으로 등록되어 Scouter 코어에서 발생하는 모든 Alert를 수신. `enable` / `sendAlert` / `skipAlertSet` / `skipFatalAlertSet` 설정으로 필터링 후 내부 `RequestQueue`에 적재.
  - `run()` — 큐 소비 스레드. `ObjectPack`을 `AgentManager`에서 조회해 `objName` / `serviceGroup` 설정, 필요 시 `ChartUtil` / `SQLUtil`로 차트·SQL을 S3에 업로드.
- **[SlackMessageUtil.java](tuna.server.plugin.slack/src/main/java/scouter/plugin/server/slack/util/SlackMessageUtil.java)**
  - `getSlackMessage(AlertVo)` — `AlertVo`를 Slack Block Kit `Payload`(JSON)로 변환. 서버명·메시지·레벨·태그를 마크다운으로 구성. Chart/SQL/TunA URL은 사내 시스템 전용이라 실제 Slack 전송본에는 포함하지 않음(빈 `if` 블록으로 남아있음).
- **[AlertSender.java](tuna.server.plugin.slack/src/main/java/scouter/plugin/server/slack/http/AlertSender.java)**
  - 싱글톤 데몬 스레드. `serviceGroup`(콤마 구분 다중 시스템 가능)별로 `PluginConfigItem`에서 채널 Webhook Endpoint를 조회해 각각 HTTP POST 전송. 429 에러/실패는 로깅 후 재시도 없이 무시.
- S3 업로드 관련: `useS3: true` 설정 시에만 S3 인스턴스(`AutoDeleteS3`) 생성, `plugin_slack.yaml`은 필요한 최소 항목만 유지.

#### 상세 흐름 — Alert 발생부터 Slack 전송까지

```
Scouter Core
   └─ AlertPack 발행
        └─ AlertToSlack.process()  [Core 스레드에서 호출]  ─ 필터링 후 queue에 적재
             └─ AlertToSlack.run()  [AlertToSlack 데몬 스레드]  ─ 큐 소비, AlertVo 구성
                  └─ SlackMessageUtil.getSlackMessage()  ─ MessageVo(JSON) 생성
                       └─ AlertSender.add()  ─ 큐에 적재
                            └─ AlertSender.run()  [AlertSender 데몬 스레드]  ─ 큐 소비
                                 └─ AlertSender.transfer()  ─ 채널별 HTTP POST 전송
```

> 한 줄 요약: `process()`가 큐에 넣고(producer), `run()` 스레드가 큐에서 꺼내며(consumer, 블로킹 방식), 꺼낸 건마다 `SlackMessageUtil`을 한 번 호출해 메시지로 변환하고, 그 결과를 다시 `AlertSender`의 큐로 넘기는 구조.

**1단계 — 수신 및 필터링** ([AlertToSlack.java:72-112](tuna.server.plugin.slack/src/main/java/scouter/plugin/server/slack/AlertToSlack.java#L72-L112))

Scouter 코어 스레드가 `@ServerPlugin(PluginConstants.PLUGIN_SERVER_ALERT)`으로 등록된 `process(AlertPack)`을 직접 호출한다. 가드 클로즈 체인이라 하나라도 걸리면 그 즉시 드롭된다.

1. `alertPack.level == 0`(info) → 무조건 스킵
2. `pluginConfigItem`을 매번 새로 조회(`pluginConfigure.getPluginConfigItem()`) — null이면 스킵
3. `enable == false` → 플러그인 전체 비활성 시 스킵
4. `sendAlert == false` → 전송 자체가 꺼져 있으면 스킵
5. `skipAlertSet`에 해당 alertType이 있으면 스킵 (타입 단위 차단)
6. `checkAlertWarning()` — level==1(warn)이고 skip 대상이면 스킵
7. `checkAlertFatal()` — level==3(fatal)이고 skip 대상이면 스킵

여기까지 통과한 것만 `RequestQueue<AlertPack> queue`(용량 100)에 적재된다. `pluginConfigItem`은 인스턴스 필드지만 `volatile`이 아니라서, 설정 리로드 중에 다른 스레드가 과도기 값을 볼 수 있는 여지가 있다.

**2단계 — 큐 소비 및 AlertVo 구성** ([AlertToSlack.java:147-188](tuna.server.plugin.slack/src/main/java/scouter/plugin/server/slack/AlertToSlack.java#L147-L188))

`AlertToSlack` 자신의 데몬 스레드(`run()`)가 큐를 소비한다. 두 갈래로 분기된다.

- **Cloud Alert 경로** (`alertType == CLOUD_ALERT`): `objName="CLOUD"`, `serviceGroup="DEFAULT"`로 고정하고 곧바로 메시지 생성 단계로 넘어감(`continue`). `ObjectPack` 조회, Chart/SQL 업로드를 전부 건너뜀.
- **일반 Alert 경로** (Host/Application Agent): `AgentManager.getAgent(objHash)`로 `ObjectPack` 조회.
  - `objectPack`이 null이면 — 처리하지 않고 조용히 다음 루프로 (메시지 미생성)
  - `objectPack.getObjSvcGroup()`이 null이면 — 로그만 남기고 `continue`
  - 정상이면 `alertVo`에 `objName`/`serviceGroup` 설정 후, `useChartView`/`useSqlView` 설정에 따라 `ChartUtil`/`SQLUtil`로 S3 업로드

큐 동작 방식(질의사항 정리): `queue.get()`은 **블로킹 호출**이다 — 큐가 비어 있으면 스레드가 대기하다가, 새 `AlertPack`이 `process()`에서 `put()`되는 즉시 깨어나 처리한다. `Thread.sleep()`으로 몇 초마다 확인하는 polling 방식(Cloud Alert Plugin의 `CloudAlertRuleScheduler`가 10초마다 도는 방식)이 **아니라**, 이벤트 도착 즉시 반응하는 producer-consumer 구조다. `SlackMessageUtil`은 이 큐를 직접 읽지 않는다 — 상태 없는 static 유틸이며, `run()`이 큐에서 꺼낸 `AlertPack` 1건에 대해 그때그때 1회 호출되어 메시지로 변환해줄 뿐이다.

**3단계 — 메시지 생성** ([SlackMessageUtil.java:36-123](tuna.server.plugin.slack/src/main/java/scouter/plugin/server/slack/util/SlackMessageUtil.java#L36-L123))

`AlertVo`를 Slack Block Kit `Payload`로 변환한다.

- 상단 고정 블록: "This message is from TunA." + 생성 시각 + 구분선
- 본문(markdown): `*server*`(objName), `*message*`(또는 STUCK_SERVICE면 `*service*`), `*level*`(AlertLevel 이름)
- `alertPack.tags`를 순회하며 태그를 나열 — `alertType`/`sql`/`sqlparam` 키는 제외, `cpu`→"cpu time(ms)", `duration(ms)`→"elapsed(ms)"로 라벨만 변경. 값은 `StringUtil.limiting(v, 100)`으로 100자 제한
- Chart URL / SQL URL / TunA 링크는 코드상 `if` 블록만 있고 내부가 비어 있어 실제로는 포함되지 않음 — 사내 시스템 전용 링크라 의도적으로 뺀 것
- 완성된 `Payload`를 `GsonFactory.createSnakeCase()`로 JSON화 → `MessageVo.alertMesage`에 저장, `serviceGroup`도 함께 담음

**4단계 — 전송** ([AlertSender.java](tuna.server.plugin.slack/src/main/java/scouter/plugin/server/slack/http/AlertSender.java))

`AlertSender`는 클래스 로딩 시 즉시 생성되는 싱글톤 데몬 스레드(`add()` → 큐 적재 → `run()`이 소비 → `transfer()`).

`transfer()` ([AlertSender.java:100-156](tuna.server.plugin.slack/src/main/java/scouter/plugin/server/slack/http/AlertSender.java#L100-L156)):
1. `messageVo.getServiceGroup()`을 콤마로 split — 하나의 Alert가 여러 시스템(채널)으로 각각 전송될 수 있음
2. 시스템 이름마다 `pluginConfigItem.getSystemChannelEndpoint(s)`로 `plugin_slack.yaml`의 `channels` 목록에서 `system` 이름이 일치하는 webhook URL 조회 ([PluginConfigItem.java:72-81](tuna.server.plugin.slack/src/main/java/scouter/plugin/server/slack/config/PluginConfigItem.java#L72-L81))
3. 못 찾으면 해당 시스템만 로그 남기고 스킵, 나머지는 계속 진행
4. 찾으면 `HttpPost`로 JSON 바디를 그대로 webhook에 POST
5. 2xx인데 바디에 `"error 429 "`가 포함되면 Slack의 rate-limit 응답 — 이 경우 나머지 systemArr 전송을 즉시 중단(`return false`), 다른 4xx/5xx는 루프를 계속 진행하는 것과 비대칭
6. 재시도 로직 없음 — 실패하거나 예외가 나면 그 메시지는 그대로 유실됨

핵심 요약: **필터링**은 `process()`(레벨/enable/sendAlert/skip 설정), **분기**는 `run()`(Cloud vs 일반 Alert, ObjectPack 존재 여부), **변조**는 `SlackMessageUtil`(태그 필터링·100자 제한·차트/SQL 링크 의도적 배제), **전송**은 `AlertSender`(serviceGroup→webhook 매핑, 429 시 조기 중단, 재시도 없음) — 네 곳 모두 "어디서 알림이 사라질 수 있는가"를 살펴볼 지점이다.

---

### 2. Cloud Alert Plugin 

기간: 2024-11-14 ~ 2024-11-25

- Cloud Alert 플러그인 기본 구조 수립
- Cloud Alert Rule 캐싱 개발 및 설명/디버깅 방법 문서화
- `CloudAlertRule` 캐시 데이터 구조 개선, `CloudMetricPack` 수신 후 관련 Alert Rule 필터링 로직
- `PluginConfigure` 리팩토링, `AlertLevel`(warn/fatal) 검사 메소드 추가
- `filterBy` 조건 검사, Dimension 정보 반영, Resource 태그 정보 처리 등 Alert 검사 로직 다수 개선/리팩토링
- `protobuf-java` 3.22.4 보안 이슈 대응 pom.xml 갱신
- Amazon Cloud 데이터 지연 반영을 위해 `CloudMetricPack` 다건 수집 구조로 재개발 (수신 즉시 처리 → DB 재조회 방식으로 전환)
- 최종적으로 Alert 검사 대상 Pack 생성 로직의 점검 데이터 추출 오류 수정 (최신 커밋 `cb345cb`)

#### 핵심 클래스 및 로직 흐름

두 개의 독립 데몬 스레드가 협업.

- **[CloudAlertRuleScheduler.java](tuna.server.plugin.cloud.alert/src/main/java/scouter/plugin/server/cloud/alert/schedule/CloudAlertRuleScheduler.java)** (싱글톤, 10초 주기)
  - OpenSearch `cloud-alert-rule` 인덱스에서 `activeYn=Y`인 Alert Rule을 전량 읽어 `alertRuleCache`(ConcurrentHashMap)를 통째로 교체. 감시 대상 `metricMetaId` 집합도 함께 갱신.
- **[CloudAlertPlugin.java](tuna.server.plugin.cloud.alert/src/main/java/scouter/plugin/server/cloud/alert/CloudAlertPlugin.java)** (10초 주기)
  - `process(CloudMetricPack)`는 빈 껍데기 — 클라우드 메트릭이 지연 반영될 수 있어, 수신 즉시 처리 대신 DB를 재조회하는 방식으로 재설계됨(실제 처리는 `run()`).
  - `generateCloudMetricPacksForAlertCheck()` — `cloud-metric-data-*` 인덱스에서 metricMetaId+resourceId별 최신 데이터 1건씩을 집계 쿼리로 조회, `cloud-alert-latest` 인덱스의 마지막 검사 ctime보다 최신인 것만 골라 `CloudMetricPack`으로 재구성 (중복 검사 방지).
  - `processAlertCheck()` — aggType(max/min/avg 등)별로 캐시된 AlertRule 필터링.
  - `createAlertPack()` — 조건을 fatal 먼저 정렬(`sortConditions`) 후 임계치 비교(`evaluateThresholdCondition`), 리소스 태그 조건(`filterBy`)은 `removeWildcardValues`로 `*` 값 제외 후 `isResourceMatched`로 매칭 검사. 매칭 시 `AlertPack` 생성, `objType = "cloud"` 태깅 후 `AlertCore.add()`로 전체 Alert 파이프라인에 편입.
  - `upsertCloudAlertLatest()` — 처리 후 `cloud-alert-latest` 인덱스에 ctime 갱신 (중복 방지용).

---

### 3. Slack ↔ Cloud Alert 연동

기간: 2024-11-19 ~ 2024-11-21

- Slack Plugin이 Cloud Alert도 처리하도록 수정, `AlertPack`에 `objType=cloud` 전달
- S3 관련 인스턴스는 `useS3: true` 설정 시에만 생성하도록 수정, `plugin_slack.yaml` 최소화
- Slack Plugin에 `enable` 설정 추가
- Alert 메시지/레벨/타입 관련 다수 수정

#### 연동 로직

별도 클래스가 아니라 **공유 데이터 모델(`AlertPack`)의 필드를 매개로** 두 플러그인이 느슨하게 결합되어 있다.

- Cloud Alert Plugin이 `AlertPack.objType = "cloud"`, `tags.put("alertType", AlertTypeConstants.CLOUD_ALERT)`를 심어서 `AlertCore.add()`에 넣음.
- Slack Plugin은 이 Alert를 다른 일반 Alert와 동일한 큐로 받지만, `AlertToSlack.run()`에서 `alertType == CLOUD_ALERT`를 체크해 호스트 에이전트 조회 로직(`ObjectPack`, `ChartUtil`, `SQLUtil`)을 완전히 스킵하고 `objName="CLOUD"`, `serviceGroup="DEFAULT"`로 고정된 별도 경로를 태운다.
- 즉 Cloud Alert Plugin은 "감시·판정"만 담당하고 전송 책임은 지지 않으며, Slack Plugin은 태그만 보고 분기 처리하는 구조.

---

### 코드 스니펫 — 분기/동시성 참고

아래는 위 로직 중 **조건 분기가 판단 결과를 좌우하는 지점**과 **여러 스레드가 동시에 접근하는 지점**을 발췌한 것이다.

#### 1) Cloud Alert Plugin — Rule 캐시 동시성 (스케줄러 스레드 vs 처리 스레드)

`CloudAlertRuleScheduler`는 데몬 스레드로 10초마다 `loadCloudAlertRule()`을 실행해 캐시를 갱신하고, `CloudAlertPlugin`은 별도의 데몬 스레드에서 10초마다 `filterAlertRules()`로 같은 캐시를 읽는다. 즉 **쓰기 스레드 1개 + 읽기 스레드 1개가 `alertRuleCache`를 동시에 드나든다.**

```java
// CloudAlertRuleScheduler.java
private static CloudAlertRuleScheduler inst = null;
private final ConcurrentHashMap<String, Object> alertRuleCache = new ConcurrentHashMap<>();

// 싱글톤 획득 - synchronized로 인스턴스 생성 시점의 race만 막음
public synchronized static CloudAlertRuleScheduler getInstance() {
    if (inst == null) {
        inst = new CloudAlertRuleScheduler();
        inst.setDaemon(true);
        inst.setName("scouter.plugin.cloud.alert.db.OpenSearchCacheScheduler");
        inst.start();
    }
    return inst;
}

public void run() {
    while (true) {
        try {
            loadCloudAlertRule(); // 캐시 갱신 - 아래 참고
            Thread.sleep(SCHEDULE_PERIOD);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

public void loadCloudAlertRule() {
    if (!conf.cloud_alert_plugin_enable) {
        return;
    }
    ConcurrentHashMap<String, Object> newCache = new ConcurrentHashMap<>();
    // ... OpenSearch에서 cloud-alert-rule 조회 후 newCache에 채움 ...

    // 캐시를 newCache로 교체 - 두 단계로 나뉘어 있어 원자적(atomic) 스왑이 아님
    alertRuleCache.clear();
    alertRuleCache.putAll(newCache);
}
```

```java
// CloudAlertPlugin.java - 다른 스레드에서 위 캐시를 읽는 지점
private void processAlertCheck(CloudMetricPack cloudMetricPack) {
    for (String aggType : cloudMetricPack.dataPoints.keySet()) {
        HashMap<String, Object> filter = new HashMap<>();
        filter.put("metricMetaId", cloudMetricPack.metricMetaId);
        filter.put("aggType", aggType);

        // alertRuleCache를 읽음 - loadCloudAlertRule()의 clear()~putAll() 사이 타이밍과
        // 겹치면 순간적으로 빈 캐시(또는 절반만 채워진 캐시)를 볼 수 있음
        HashMap<String, Object> alertRules = this.alertRuleScheduler.filterAlertRules(filter);
        if (!alertRules.isEmpty()) {
            // ... AlertPack 생성 ...
        }
    }
}
```

**주의점**: `clear()` → `putAll()` 사이에 `filterAlertRules()`가 호출되면 일시적으로 빈 Map을 순회하게 된다. `ConcurrentHashMap` 자체는 개별 연산에 대해 스레드-safe하지만, "지우고 다시 채우는" 두 연산의 조합은 원자적이지 않다. 최악의 경우 10초 주기의 한 사이클에서 Alert Rule을 못 찾아 검사를 건너뛸 수 있음 (다음 사이클에 다시 검사되므로 데이터 유실은 아니지만 지연 발생 가능).

#### 2) Cloud Alert Plugin — Alert 판정 분기 (fatal 우선 평가, 임계치 비교, 태그 매칭)

`createAlertPack()`은 하나의 메트릭에 대해 여러 조건(warn/fatal)이 걸려 있을 때 **어떤 조건이 먼저 매칭되면 그걸로 확정하고 리턴**하는 구조라, 평가 순서가 결과를 좌우한다. 그래서 `sortConditions()`로 fatal을 먼저 오도록 정렬해둔다.

```java
// CloudAlertPlugin.java
private void sortConditions(ArrayList<Map<String, Object>> conditions) {
    conditions.sort((map1, map2) -> {
        String alertLevel1 = (String) map1.get("alertLevel");
        String alertLevel2 = (String) map2.get("alertLevel");
        if ("fatal".equals(alertLevel1) && !"fatal".equals(alertLevel2)) {
            return -1; // fatal을 앞으로
        } else if (!"fatal".equals(alertLevel1) && "fatal".equals(alertLevel2)) {
            return 1;
        } else {
            return 0;
        }
    });
}

private AlertPack createAlertPack(CloudMetricPack cloudMetricPack, String aggType, Map<String, Object> alertRules) {
    Value value = cloudMetricPack.dataPoints.get(aggType);
    ArrayList<Map<String, Object>> thresholdConditions = (ArrayList<Map<String, Object>>) alertRules.get("conditions");
    sortConditions(thresholdConditions); // fatal이 먼저 평가되도록

    for (Map<String, Object> thresholdCondition : thresholdConditions) {
        // 조건에 하나라도 매칭되면 그 자리에서 AlertPack을 만들어 즉시 return
        // -> 뒤에 있는(=fatal보다 낮은) 조건은 평가되지 않음
        if (evaluateThresholdCondition(value, thresholdCondition)) {
            Map<String, Object> resourceInfo = getResourceInfo(cloudMetricPack);
            Map<String, Object> filterBy = (Map<String, Object>) alertRules.get("filterBy");
            filterBy = removeWildcardValues(filterBy);
            if (isResourceMatched(resourceInfo, filterBy)) {
                AlertPack alertPack = new AlertPack();
                // ...
                String thresholdConditionLevel = (String) thresholdCondition.get("alertLevel");
                if (thresholdConditionLevel.equals("warn")) {
                    alertPack.level = AlertLevel.WARN;
                    // ...
                } else if (thresholdConditionLevel.equals("fatal")) {
                    alertPack.level = AlertLevel.FATAL;
                    // ...
                }
                return alertPack; // 첫 매칭에서 즉시 확정
            }
        }
    }
    return null; // 매칭되는 조건 없음
}

private boolean evaluateThresholdCondition(Value value, Map<String, Object> condition) {
    String conditionType = (String) condition.get("conditionType");
    double thresholdValue = ((Number) condition.get("thresholdValue")).doubleValue();
    double metricValue = ((Number) value).doubleValue();
    switch (conditionType) {
        case ">":  return metricValue > thresholdValue;
        case ">=": return metricValue >= thresholdValue;
        case "<":  return metricValue < thresholdValue;
        case "<=": return metricValue <= thresholdValue;
        default:   return false; // 알 수 없는 연산자는 무조건 미매칭
    }
}
```

리소스 태그 매칭(`isResourceMatched`)도 **`filterBy`가 비어있는지, 태그 값이 배열인지 단일값인지**에 따라 분기 경로가 다르다.

```java
// CloudAlertPlugin.java
private boolean isResourceMatched(Map<String, Object> resourceInfo, Map<String, Object> tagsInFilterby) {
    if (resourceInfo.isEmpty()) {
        return false; // 리소스 정보를 못 가져왔으면 무조건 불일치
    }
    if (tagsInFilterby.isEmpty()) {
        return true; // 필터 조건이 없으면 무조건 매칭 (전체 대상)
    }

    for (String tagClassification : tagsInFilterby.keySet()) { // sysTags / customTags
        Map<String, Object> tagsInResource = (Map) resourceInfo.get(tagClassification);
        Map<String, Object> tagsInFilter = (Map) tagsInFilterby.get(tagClassification);

        for (String filterTageName : tagsInFilter.keySet()) {
            if (!tagsInResource.containsKey(filterTageName)) {
                return false; // 필터에 있는 태그가 리소스에 없으면 즉시 불일치
            }
        }
        for (String filterTagName : tagsInFilter.keySet()) {
            String resourceTagValue = tagsInResource.get(filterTagName).toString();
            if (tagsInFilter.get(filterTagName) instanceof ArrayList) {
                // 필터 값이 배열이면 "포함 여부"로 판단 (OR 조건)
                ArrayList<String> filterTagValues = (ArrayList<String>) tagsInFilter.get(filterTagName);
                if (!filterTagValues.contains(resourceTagValue)) {
                    return false;
                }
            } else {
                // 단일 값이면 완전 일치로 판단
                if (!tagsInFilter.get(filterTagName).equals(resourceTagValue)) {
                    return false;
                }
            }
        }
    }
    return true;
}
```

#### 3) Cloud Alert Plugin — 중복 검사 방지 분기 (ctime 비교)

DB 재조회 방식으로 바뀐 핵심 분기점. `latestCtimeMap`에 기록이 있는지, 있다면 더 최신 데이터인지에 따라 검사 대상 여부가 갈린다.

```java
// CloudAlertPlugin.java
private ArrayList<CloudMetricPack> generateCloudMetricPacksForAlertCheck() {
    ArrayList<CloudMetricPack> cloudMetricPacks = new ArrayList<>();
    List<Map<String, Object>> metricDataList = getMetricDataList();
    HashMap<Long, HashMap<Long, Long>> latestCtimeMap = getCloudAlertLatestCtimeMap();

    for (Map<String, Object> metricData : metricDataList) {
        long metricMetaId = Long.parseLong(metricData.get("metricMetaId").toString());
        long resourceId = Long.parseLong(metricData.get("resourceId").toString());
        long ctime = Long.parseLong(metricData.get("ctime").toString());

        boolean isAlertRuleCheckRequired = false;

        // 기록이 있고, 기록된 시각보다 더 최신 데이터일 때만 재검사
        if (latestCtimeMap.containsKey(metricMetaId) && latestCtimeMap.get(metricMetaId).containsKey(resourceId)) {
            long latestCtime = latestCtimeMap.get(metricMetaId).get(resourceId);
            if (ctime > latestCtime) {
                isAlertRuleCheckRequired = true;
            }
        } else {
            // 기록이 없으면(최초 검사) 무조건 검사 대상
            isAlertRuleCheckRequired = true;
        }

        if (isAlertRuleCheckRequired) {
            // CloudMetricPack 생성 후 목록에 추가
        }
    }
    return cloudMetricPacks;
}
```

이 분기가 최근 커밋(`cb345cb`, "점검 데이터 추출 오류 수정")에서 손댄 지점으로 추정된다 — `metricData`에서 필요한 필드(`metricMetaId`/`resourceId`/`ctime`/`dataPoints`)를 추출하는 과정의 오류였을 가능성이 높다.

#### 4) Slack Plugin — Alert 필터링 체인 (여러 조건이 순차적으로 알림을 걸러냄)

`process()`는 하나라도 조건에 걸리면 즉시 `return`하는 **가드 클로즈(guard clause) 체인**이라, 순서가 바뀌면 필터링 결과가 달라질 수 있다.

```java
// AlertToSlack.java
@ServerPlugin(PluginConstants.PLUGIN_SERVER_ALERT)
public void process(AlertPack alertPack) {
    if (alertPack.level == 0) return; // info 레벨은 무조건 스킵

    pluginConfigItem = pluginConfigure.getPluginConfigItem();
    if (pluginConfigItem == null) {
        Logger.println("P21", "pluginConfigItem is null. check config file exist or valid.");
        return;
    }
    if (!pluginConfigItem.isEnable()) { return; }         // 플러그인 전체 비활성
    if (pluginConfigItem.isSendAlert() == false) { return; } // 전송 자체를 끔
    if (pluginConfigItem.getSkipAlertSet().contains(alertPack.getAlertType())) { return; } // 타입별 스킵
    if (checkAlertWarning(alertPack)) { return; } // warn 레벨 + skip 대상
    if (checkAlertFatal(alertPack)) { return; }   // fatal 레벨 + skip 대상

    boolean b = this.queue.put(alertPack); // 여기까지 걸리지 않은 것만 큐에 적재
    if (!b) {
        Logger.println("P02", "Slack plugin queue is null.");
    }
}
```

**동시성 참고**: `process()`는 Scouter 코어 스레드에서 호출되고, `run()`은 `AlertToSlack`의 데몬 스레드에서 큐를 소비한다. `pluginConfigItem`은 인스턴스 필드인데 `process()`가 매번 `pluginConfigure.getPluginConfigItem()`으로 재조회해 갱신하므로, 설정 리로드 중에는 다른 스레드가 갱신 중인 값을 읽을 수도 있다 (필드에 `volatile` 없음).

#### 5) Slack Plugin — Cloud Alert 분기 처리 (연동 지점)

`run()`에서 클라우드 Alert인지 여부로 처리 경로가 완전히 분리된다. 이 `if` 블록이 Cloud Alert Plugin과의 연동 지점이다.

```java
// AlertToSlack.java
public void run() {
    while (CoreRun.running()) {
        try {
            AlertPack alertPack = queue.get(); // 블로킹 - 큐가 비어있으면 대기

            if (alertPack.getAlertType() == AlertTypeConstants.CLOUD_ALERT) {
                // Cloud Alert 전용 경로: ObjectPack 조회, Chart/SQL 업로드를 전부 스킵
                AlertVo alertVo = new AlertVo(alertPack);
                alertVo.setServiceGroup("DEFAULT");
                alertVo.setObjName("CLOUD");
                MessageVo messageVo = SlackMessageUtil.getSlackMessage(alertVo);
                alertSender.add(messageVo);
                continue; // 아래 일반 Alert 처리 로직은 실행 안 함
            }

            // 일반 Alert(Host/Application Agent) 경로
            AlertVo alertVo = new AlertVo(alertPack);
            ObjectPack objectPack = getObjectPack(alertPack.objHash);
            if (objectPack != null) {
                String serviceGroup = objectPack.getObjSvcGroup();
                if (serviceGroup == null) {
                    Logger.println("P22", "system info doesn't exist for " + objectPack.objName);
                    continue; // 시스템 정보 없으면 스킵 - Cloud Alert 경로와 달리 여기선 continue만
                }
                alertVo.setObjName(objectPack.objName);
                alertVo.setServiceGroup(objectPack.getObjSvcGroup());
                if (pluginConfigItem.isUseChartView()) {
                    String chartUrl = ChartUtil.getInstance().uploadChart(alertVo);
                    if (chartUrl != null && alertPack.getAlertType() == AlertTypeConstants.CPU_USED_PCT) {
                        lastCpuAlertChartUrl.put(alertPack.objHash, chartUrl);
                    }
                }
                if (pluginConfigItem.isUseSqlView()) {
                    SQLUtil.getInstance().uploadSql(alertVo);
                }
                MessageVo messageVo = SlackMessageUtil.getSlackMessage(alertVo);
                alertSender.add(messageVo);
            }
            // objectPack이 null이면 아무 처리도 안 하고 다음 루프로 (암묵적 스킵)
        } catch (Exception ex) {
            scouter.server.Logger.println("P04", 10, ex.getMessage(), ex);
        }
    }
}
```

`lastCpuAlertTimeMap` / `lastCpuAlertChartUrl`은 `ConcurrentHashMap`으로 선언되어 있어 여러 스레드에서의 접근을 고려했지만, 실제로 이 클래스 안에서는 `run()` 스레드 하나만 쓰고 있어 현재로선 과설계에 가깝다(다른 스레드에서 참조하는 코드는 없음).

#### 6) Slack Plugin — 전송 큐와 채널별 반복 전송 (재시도 없음)

`AlertSender`는 싱글톤이자 자체 데몬 스레드로, `AlertToSlack.run()` 스레드가 `add()`로 넣고 자신의 `run()`이 소비하는 별도의 Producer-Consumer 큐를 가진다.

```java
// AlertSender.java
private static final AlertSender inst = new AlertSender(); // 클래스 로딩 시 즉시(eager) 초기화

private AlertSender() {
    createClient();
    this.setDaemon(true);
    this.setName("TunA Slack plugin alert sender thread.");
    if (httpClient != null) {
        this.start(); // 클라이언트 생성 실패 시 스레드 자체를 시작하지 않음
    }
}

public void add(MessageVo messageVo) {
    if (httpClient == null) return; // 초기화 실패 상태면 조용히 드롭
    boolean b = queue.put(messageVo);
    if (!b) {
        Logger.println("P03", 10, "alert sender queue full.");
    }
}

public void run() {
    while (CoreRun.running()) {
        MessageVo messageVo = queue.get(); // 블로킹 소비
        transfer(messageVo);
    }
}

public boolean transfer(MessageVo messageVo) {
    boolean result = true;
    try {
        String systems = messageVo.getServiceGroup();
        if (systems == null) return false;

        StringEntity entity = new StringEntity(messageVo.getAlertMesage());
        String[] systemArr = systems.split(","); // 하나의 Alert가 여러 시스템(채널)으로 각각 전송될 수 있음
        for (String s : systemArr) {
            String slackChannelEndpoint = pluginConfigItem.getSystemChannelEndpoint(s);
            if (slackChannelEndpoint == null) {
                Logger.println("P13", "no suitable slack channel for " + s);
                continue; // 채널을 못 찾으면 해당 시스템만 스킵하고 나머지는 계속 전송
            }
            HttpPost httpPost = new HttpPost(URI.create(slackChannelEndpoint));
            httpPost.setEntity(entity); // StringEntity를 여러 요청에 재사용 (엔티티는 read-once가 아니라 문제 없음)
            try (CloseableHttpResponse httpResponse = httpClient.execute(httpPost)) {
                int scode = httpResponse.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(httpResponse.getEntity(), StandardCharsets.UTF_8);
                if (scode >= 200 && scode < 300) {
                    if (responseBody.contains("error 429 ")) {
                        // Slack Rate Limit 응답 - HTTP 200으로 오되 바디에 에러가 실려오는 케이스
                        return false; // 재시도 로직 없이 바로 실패 처리, 남은 systemArr 전송 안 함
                    }
                    result = true;
                } else {
                    result = false; // 실패해도 루프는 계속 - 다음 시스템으로 전송 시도
                }
            } catch (Exception e) {
                result = false; // 예외 발생해도 루프는 계속
            }
        }
    } catch (Exception ex) {
        result = false;
    }
    return result;
}
```

**주의점**:
- 429(rate limit) 응답을 만나면 `systemArr`의 나머지 시스템에 대한 전송이 즉시 중단된다(`return false`) — 다른 오류(4xx/5xx)는 계속 진행하는 것과 비대칭적인 분기.
- 실패해도 재시도가 없어, 큐에서 이미 꺼낸 메시지(`queue.get()`)는 전송 실패 시 유실된다.
- `httpClient`는 인스턴스 생성 시 한 번만 만들어지고 이후 재생성 로직이 없어, TLS 핸드셰이크 실패 등으로 초기화가 실패하면 이후 플러그인 수명 동안 계속 무동작 상태가 된다.

