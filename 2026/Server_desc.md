## 프로젝트 개요

`tuna-scouter-server` (모듈명 `tuna.scouter.server`)는 오픈소스 APM인 [Scouter](https://github.com/scouter-project/scouter) 서버를 포크하여, OpenSearch/Elasticsearch 기반 영속성과 클라우드/K8s 모니터링 기능을 얹은 "TunA Server"입니다. 단독으로 빌드/실행되는 프로젝트가 아니라 형제 프로젝트들과 함께 구성되는 멀티 리포 워크스페이스의 일부입니다 (`.classpath` 참고: `scouter-common`, `scouter-server`, `tuna.scouter.common`, `tuna.scouter.deploy`, 그리고 배포 시 필요한 `../tuna.server.boot`).

## 빌드 & 테스트 명령어

- 기본 빌드: `mvn package` (`defaultGoal`이 `package`로 지정됨)
- **테스트는 기본적으로 꺼져 있습니다** (`maven.test.skip=true`, `maven.test.failure.ignore=true`). 테스트를 실행하려면 반드시 오버라이드해야 합니다:
  - 전체 테스트: `mvn test -Dmaven.test.skip=false`
  - 단일 클래스: `mvn test -Dmaven.test.skip=false -Dtest=XLogESRDTest`
  - 단일 메서드: `mvn test -Dmaven.test.skip=false -Dtest=XLogESRDTest#methodName`
  - 테스트 프레임워크: JUnit 5 (jupiter) + Mockito + AssertJ, `src/test/java` 하위에 `src/main/java`와 동일한 패키지 구조로 위치.
- Java 17로 컴파일되지만 (`maven-compiler-plugin` `release=17`), `src/main/scala`에 Scala 2.12.19 소스도 존재하며 `scala-maven-plugin`이 `process-resources` 단계에서 `add-source`+`compile`로 먼저 컴파일됩니다. `scouter.server.*` 아래 클래스를 찾을 때는 Java/Scala 트리 양쪽을 확인해야 합니다 (같은 패키지 경로에 서로 다른 클래스들이 나뉘어 있음, 중복 아님).
- 환경별 프로파일: `local`(기본값), `dev`, `aws-dev`, `aws-demo`, `deploy`, `ee-deploy`, `sonar` — 예: `mvn package -P dev`. 각 프로파일은 `profile/<env>/conf`, `profile/<env>/script`를 패키징 결과물에 포함시킵니다.
- `package` 단계에 바인딩된 `maven-antrun-plugin`(`make-deploy-package`)이 실행 가능한 `output/` 디렉터리를 구성합니다: 형제 프로젝트 `../tuna.server.boot`의 `boot.jar`를 복사하고, 이 모듈의 jar + `target/lib` 의존성, 그리고 `profile/<env>/{conf,script,lib}`를 함께 배치합니다.
- ⚠️ `mvn install -P dev`는 `install` 단계에 바인딩된 `deploy-devServer` 실행을 트리거하여 **실제 개발 서버에 SSH로 접속해 WAS를 정지/배포/기동**합니다. 사용자의 명시적 확인 없이 실행하지 마세요.
- SonarQube 분석: `mvn sonar:sonar -P sonar` (서버 주소/자격증명이 pom.xml에 하드코딩되어 있음).

## 아키텍처

### 패키지 이원 구조: `scouter.*` vs `tuna.*`
- `scouter.*` (Java + Scala 둘 다 존재) — 원본 Scouter APM 서버 코어: 에이전트-서버 프로토콜(UDP/TCP), 커맨드 디스패치, 플러그인 SPI, 알럿, HTTP 대시보드 API.
- `tuna.*` — TunA에서 추가한 확장 계층: OpenSearch/Elasticsearch 영속성(`tuna.server.db`), 라이선스(`tuna.server.license`), 클라우드/K8s 수집 후처리(`tuna.server.pack.ext.processor`), 카운터/히트맵 등.

### 부트스트랩 (Main 두 개, 하나가 다른 하나를 호출)
1. `tuna.server.Main` (실제 실행되는 Main) — `t2.home` 경로 해석, 시스템 프로퍼티 설정, TunA 전용 싱글턴 초기화(`ElasticConnectionManager`, `CounterManager`/`CounterWorker`, GeoIp, 라이선스, 각종 WR Factory 등), `PackExtProcessChain`에 pack 후처리기 등록 후 `scouter.server.Main.main(args)` 호출.
2. `scouter.server.Main` — 원본 Scouter 서버 구성요소 부트: `Configure`, 라이선스, ES 커넥션, `AlertEngine`, `ServiceHandlingProxy`(커맨드 디스패치), `DataUdpServer`/`TcpServer`(에이전트 프로토콜), `HttpServer`(Jetty, 대시보드/API), `PlugInManager`/`BuiltInPluginManager`, 스케줄러(AutoDelete, AiCacheRefresh, AnomalyDetect), `AlertRuleManager`.

### 에이전트↔서버 프로토콜 디스패치 (`scouter.server.netio.service`)
서버 커맨드 핸들러 메서드에는 `@ServiceHandler("<cmd>")` 애너테이션을 붙입니다. `ServiceHandlingProxy.load()`가 부팅 시 클래스패스를 스캔(`scouter.util.scan.Scanner`, 그리고 `scouter.handler` 시스템 프로퍼티로 지정된 클래스도 포함)해서 `cmd -> method` 맵을 만들고, 이후 TCP/UDP로 들어오는 패킷은 `ServiceHandlingProxy.process(cmd, in, out, login)`이 리플렉션으로 디스패치합니다. 새 서버 커맨드를 추가할 때는 별도의 등록 코드 없이 클래스패스 어딘가에 `@ServiceHandler` 메서드만 추가하면 됩니다.

### ServiceHandlingProxy 상세 로직 (graphify로 확인됨)
`PlugInLoader`(파일 스크립트+javassist, 5초 폴링)나 `BuiltInPluginManager`(애너테이션 스캔, 훅당 N개)와는 또 다른 세 번째 확장 지점 패턴입니다 — `BuiltInPluginManager`와 마찬가지로 애너테이션+클래스패스 스캔이지만, **키(cmd)당 인스턴스 1개(마지막 등록이 승리)** + **부팅 시 1회 스캔뿐, 라이브 리로드 없음**이라는 점이 다릅니다.

- **`load()`** (`main/java/scouter/server/netio/service/ServiceHandlingProxy.java:59`, `Main.java:66`에서 1회 호출) — 자기 패키지(`scouter.server.netio.service` 이하, `handle/*.scala` 등)를 재귀 스캔 + `scouter.handler` 시스템 프로퍼티로 지정된 추가 패키지까지 합쳐서 후보 클래스를 얻고, `@ServiceHandler` 붙은 메서드를 `handlers: cmd -> Invocation(instance, method)`에 등록. **키 중복 시 `Warning duplicated Handler` 로그만 남기고 `handlers.put()`은 조건 없이 실행됨** — 즉 경고는 나지만 실제로는 스캔 순서상 나중에 발견된 클래스가 이전 등록을 사일런트하게 덮어씀(순서는 `Scanner`의 클래스패스 열거 순서에 좌우되어 비결정적). `PlugInLoader`/`AlertRuleLoader`와 달리 폴링 스레드가 없어 새 핸들러 추가는 재기동해야 반영됨.
- **`process(cmd, in, out, login)`** — `handlers.get(cmd)`가 없으면 `RuntimeException("no handler cmd=...")`, 있으면 `Invocation.exec()`가 `method.invoke(instance, in, out, login)`. 핸들러 메서드 시그니처는 전부 `(DataInputX din, DataOutputX dout, boolean login)` 규약 고정. `InvocationTargetException`은 원인(`getCause()`)을 풀어서 스택트레이스를 출력해 핸들러 내부 예외의 실제 위치가 보이게 함.
- **호출부 — `TcpServiceWorker.scala`의 `ServiceWorker`** (TCP 커넥션당 1개, `main/scala/scouter/server/netio/service/net/TcpServiceWorker.scala:135`): 소켓이 열리면 먼저 4바이트 `cafe` 코드로 용도를 구분 — `TCP_AGENT`/`TCP_AGENT_V2`/`TCP_AGENT_REQ`는 각각 `TcpAgentManager`/`TcpAgentReqWorker`의 별도 장기연결 워커로 위임되고 **`ServiceHandlingProxy`를 타지 않음**. `TCP_CLIENT`(대시보드/클라이언트 커넥션)만 아래 커맨드 루프로 진입, **한 소켓이 여러 커맨드를 순차 처리**(세션 상태를 연결 단위로 재사용):
  ```scala
  while (true) {
      val cmd = in.readText()
      if (RequestCmd.CLOSE.equals(cmd)) return
      val session = in.readLong()
      if (!sessionOk && !RequestCmd.isFreeCmd(cmd)) {
          sessionOk = LoginManager.okSession(session)
          if (!sessionOk) { out.writeByte(TcpFlag.INVALID_SESSION); throw ... }  // 연결 종료
      }
      ServiceHandlingProxy.process(cmd, in, out, sessionOk)
      out.writeByte(TcpFlag.NoNEXT); out.flush()
  }
  ```
  `RequestCmd.isFreeCmd(cmd)`인 커맨드는 세션 검증 없이 통과, 그 외엔 최초 1회 `LoginManager.okSession()`으로 검증 후 `sessionOk`를 루프 내내 재사용. 핸들러가 `dout.writeByte(TcpFlag.HasNEXT)` + 페이로드를 원하는 만큼 쓰고 나면, 바깥 루프가 항상 마지막에 `TcpFlag.NoNEXT`를 붙여 응답 프레임을 종료("HasNEXT 블록 0개 이상 + 마지막 NoNEXT" 프레이밍 규약).

```
Main.java 부팅
    └─ ServiceHandlingProxy.load()  (1회, 재기동 전까지 갱신 없음)
           │  scouter.server.netio.service 패키지 + scouter.handler 시스템프로퍼티 패키지 스캔
           ▼
       handlers[cmd] = Invocation(instance, method)   ← 키 중복 시 마지막이 승리(경고만 로그)

────────────────────────── TCP 클라이언트 커넥션 (TcpServiceWorker) ──────────────────────────
소켓 open → cafe 코드 판별
    ├─ TCP_AGENT/TCP_AGENT_V2/TCP_AGENT_REQ → 별도 장기연결 워커 (ServiceHandlingProxy 안 탐)
    └─ TCP_CLIENT → while(true) 커맨드 루프
             cmd 읽기 → CLOSE면 종료
             session 검증 (isFreeCmd 아니면 LoginManager.okSession, 실패 시 연결 종료)
             ServiceHandlingProxy.process(cmd, in, out, sessionOk)
                 └─ handlers.get(cmd).exec() → method.invoke(instance, in, out, login)
                        └─ 핸들러가 HasNEXT+페이로드 0개 이상 작성
             out.writeByte(NoNEXT)  ← 응답 프레임 종료, 다음 커맨드로 루프
```

### DB 레이어 (`tuna.server.db`)
엔티티마다 동일한 패턴을 따릅니다: `I<Entity>RD`/`I<Entity>WR` 인터페이스, `<Entity>RDFactory`/`<Entity>WRFactory` 정적 팩토리(현재는 항상 ES 구현체로 귀결되지만 백엔드 교체를 위한 간접화 목적), 그리고 `ElasticCommonDao`/`ElasticManager`/`ConnectionManager` 위에 구현된 `<Entity>ESRD`/`<Entity>ESWR`. RD=조회, WR=적재. Cloud 도메인 엔티티(`CloudAgent`, `CloudConfig`, `CloudMetric`, `CloudObject`, `CloudResources`, `CloudXray` 등)도 모두 이 RD/WR 분리 패턴을 그대로 따릅니다.

### 플러그인 SPI & 알럿 (`scouter.server.plugin`)
`IPlugIn`의 하위 클래스들(`IAlert`, `ICounter`, `IMetric`, `IObject`, `ISummary`, `IText`, `IXLog`, `IXLogProfile`, `ICloudMetric` — 인터페이스가 아니라 빈 `process(pack){}` 훅을 가진 베이스 클래스)이 특정 pack 타입 처리 시점에 후킹할 수 있게 해줍니다. **`PlugInLoader`는 jar를 로드하는 게 아니라, `AlertRuleLoader`와 동일하게 `Configure.plugin_dir`의 고정 파일명 스크립트(`alert.plug`/`counter.plug`/`object.plug`/`xlog.plug`/`xlogdb.plug`/`xlogprofile.plug`/`summary.plug`)를 javassist로 동적 컴파일**해 `PlugInManager`의 정적 필드(훅당 인스턴스 1개)에 꽂는 방식입니다. `BuiltInPluginManager`는 이와 별개로 `scouter.plugin.server` 패키지를 클래스패스 스캔해 `@ServerPlugin("point")` 애너테이션 메서드를 훅당 여러 개(`List<PluginInvocation>`) 등록하는, 완전히 다른 두 번째 메커니즘입니다(상세는 아래 "PlugInLoader/PlugInManager 상세 로직" 참고). 알럿 자체는 규칙 기반으로, `scouter.server.plugin.alert.AlertEngine`/`AlertRule`/`AlertConf`/`AlertRuleLoader`가 조건을 평가하고 `scouter.server.alert.sender.*`(예: `ITSMAPISender`)를 통해 발송합니다.

### PlugInLoader/PlugInManager 상세 로직 (graphify로 확인됨)
`PlugInManager`는 로직을 직접 갖지 않고, 서로 완전히 다른 두 메커니즘을 감싸는 파사드입니다.

- **메커니즘 A — `PlugInLoader`(스크립트 기반, 훅 포인트당 인스턴스 1개)**: `main/java/scouter/server/plugin/PlugInLoader.java`, 싱글턴 데몬 스레드. 5초마다 `plugin_dir`의 고정 파일명 7개(`alert.plug`, `counter.plug`, `object.plug`, `xlog.plug`, `xlogdb.plug`, `xlogprofile.plug`, `summary.plug`)의 존재/mtime을 확인 — 파일이 없으면 대응 필드를 `null`로, mtime이 바뀌었으면 `create()`로 재컴파일. `create()`는 파일 내용을 `IAlert`/`ICounter`/... 서브클래스의 `process(pack)` 메서드 바디에 그대로 주입해 javassist로 컴파일(`AlertRuleLoader.createRule()`과 동일한 트릭). 컴파일 실패 파일은 `compileErrorFiles`에 캐싱해 재시도 안 함. **훅당 슬롯이 1개뿐**이라 새 스크립트가 이전 스크립트를 완전히 대체.
  - **죽은 필드**: `PlugInManager`에 `IMetric metrics`/`ICloudMetric cloudMetrics` 필드와 `metric()`/(주석처리된) `cloudMetric()` 메서드가 있지만, `PlugInLoader.reloadIfModified()`는 `metric.plug`/`cloudmetric.plug`를 전혀 처리하지 않음 → 이 두 필드는 항상 `null`이라 스크립트 플러그인 경로가 코드상 죽어 있음.
- **메커니즘 B — `BuiltInPluginManager`(애너테이션+클래스패스 스캔, 훅 포인트당 N개)**: `main/java/scouter/server/plugin/builtin/BuiltInPluginManager.java`. `ServiceHandlingProxy`와 동일한 `scouter.util.scan.Scanner`로 `scouter.plugin.server` 패키지를 부팅 시 1회 스캔해 `@ServerPlugin("point")`가 붙은 public 메서드를 전부 찾아 `pluginMap: point -> List<PluginInvocation>`에 등록(리스트이므로 한 훅에 여러 플러그인 동시 가능). 라이브 리로드 없음(재기동해야 갱신). `invokeAllPlugins(point, pack)`이 등록된 모든 `PluginInvocation.process(pack)`(리플렉션 `method.invoke`)을 순차 호출, 개별 실패는 `G003` 로그만 남기고 나머지에 영향 없음.
- **`PlugInManager`의 각 `xxx(pack)` 메서드는 항상 B(다건)를 먼저 호출한 뒤 A(단건, 있으면)를 호출**하는 동일 패턴(`xlog`/`xlogdb`/`profile`/`active`/`alert`/`counter`/`metric`/`summary`/`text`). `load()`는 A만 기동(`PlugInLoader.getInstance()`) — B는 `Main.java`에서 별도로 순서대로 호출(`PlugInManager.load(); BuiltInPluginManager.loadPlugins();`, `Main.java:69-70`).

```
Main.java 부팅
    ├─ PlugInManager.load() ─────▶ PlugInLoader.getInstance() (데몬 스레드, 5초 폴링)
    │                                   │ .plug 파일 mtime 변경 감지 시
    │                                   ▼
    │                             create(): javassist로 인라인 스크립트 컴파일
    │                                   │
    │                                   ▼
    │                             PlugInManager.{alerts,counters,objects,xlog,xlogdb,xlogProfiles,summary} 슬롯 교체 (훅당 1개)
    │
    └─ BuiltInPluginManager.loadPlugins() ─▶ scouter.plugin.server 패키지 1회 스캔
                                            │ @ServerPlugin("point") 붙은 메서드 수집
                                            ▼
                                      pluginMap[point] = List<PluginInvocation> (훅당 N개)

──────────────────────────── 런타임 (예: PerfCountCore) ────────────────────────────
PlugInManager.counter(pack)
    ├─ BuiltInPluginManager.invokeAllPlugins(PLUGIN_SERVER_COUNTER, pack)  → N개 순차 호출 (리플렉션)
    └─ if (counters != null) counters.process(pack)                       → 스크립트 플러그인 1개 (있으면)
```

### 알럿 룰 엔진 상세 흐름 (`scouter.server.plugin.alert`, graphify로 확인됨)
`AlertEngine`은 로직을 직접 갖지 않는 얇은 오케스트레이터입니다. 룰 로딩(파일 감시+동적 컴파일)은 `AlertRuleLoader`, 카운터 상태/히스토리는 `RealCounter`, 발송 억제/전달은 `AlertUtil`이 담당합니다.

- **`AlertRuleLoader`** (싱글턴 데몬 스레드) — 5초마다 `Configure.plugin_dir`을 스캔해 `.alert`/`.conf` 파일의 신규/변경/삭제를 감지. `.alert` 파일 내용은 **javassist로 `AlertRule.process(RealCounter c){ ... }`의 메서드 바디에 그대로 주입**되어 동적 컴파일됨(즉 `.alert` 파일 = 인라인 자바 코드 조각, `$counter` 변수로 `RealCounter` 접근). 컴파일 실패 파일은 `compileErrorFiles`에 시그니처를 캐싱해 재시도 폭주를 막음. 결과는 `alertRuleTable`/`alertConfTable`(카운터명 키)에 저장.
- **`AlertEngine.putRealTime(key, value)`** — `PerfCountCore`가 REALTIME 카운터마다 호출하는 유일한 진입점. `alertRuleTable`에 룰이 없으면 즉시 스킵. `realTimeMap`(`LinkedMap`, 최대 3000개)에서 `RealCounter` 조회/생성 후 `AlertConf`(history_size/silent_time/check_term) 변경 여부를 확인해 반영, 값 갱신 후 `checkTerm > 0`이면 마지막 체크 이후 `checkTerm`초가 지났을 때만(스로틀링) `rule.process(realCounter)` 호출, `checkTerm == 0`이면 매번 호출.
- **`AlertUtil.alert(level, counter, title, msg)`** — 룰 스크립트가 `c.warn()/error()/fatal()` 등을 호출하면 최종적으로 여기로 옴. `silentTime` 이내 동일 레벨 중복 알럿이면 무시(디바운스), 아니면 `AlertPack` 생성 후 `AlertCore.add(p)`로 실제 발송 경로에 위임.

```
PerfCountCore.add(pack) [REALTIME]
        │
        ▼
AlertEngine.putRealTime(key, value)
        │  (alertRuleTable 조회 → 없으면 스킵)
        ▼
RealCounter (realTimeMap 캐시, history 누적) ── AlertRuleLoader가 백그라운드로 갱신하는
        │        checkTerm 스로틀링                alertRuleTable / alertConfTable 참조
        ▼
AlertRule.process(RealCounter)   ← .alert 파일이 javassist로 동적 컴파일된 코드
        │
        ▼
RealCounter.warn/error/fatal() → AlertUtil.alert()
        │   (silentTime 기반 중복 억제)
        ▼
AlertCore.add(AlertPack)  →  (이후 알럿 발송 체인)
```

### HTTP/대시보드 레이어 (`scouter.server.http`)
기능 영역별 Jetty 서블릿(`ApiServlet`, `CounterServlet`, `K8S`/`Lena`/`Uxm`/`Docker`/`OpenAI`/`Llm`/`Register`/`SasApi` 등)이 대부분 `ApiProcessFactory`로 위임하며, 이 팩토리는 요청 파라미터에 따라 `scouter.server.http.processor.impl`의 수많은 `*ProcessorImpl`(모두 `ApiProcessor` 구현) 중 하나로 디스패치합니다.

### 설정
`scouter.server.Configure`(+ `scouter.config.CommonConfigure`)가 `t2.home`/`scouter.config` 시스템 프로퍼티로 지정된 `conf/t2.conf`(key=value 형식)를 로드합니다. `tuna.server.property.Property`는 TunA 전용 설정을 담습니다. 환경별 conf/script는 `profile/<env>/conf`, `profile/<env>/script`에 있으며 `make-deploy-package` antrun 실행이 패키징된 `output/` 디렉터리로 복사합니다.

### 결합 허브: `Configure`와 `ConnectionManager` (graphify 그래프 분석 결과)
`src/`를 대상으로 만든 knowledge graph(`graphify-out/`)에서 확인된 두 개의 최상위 god node이며, 이 코드베이스를 건드릴 때 blast radius를 가늠하는 기준점이 됩니다.

- **`scouter.server.Configure`** (244 edges, 그래프 전체 1위) — `conf/t2.conf`의 모든 설정값을 담는 전역 싱글턴(`getInstance()`). DB WR/RD 전체, 라이선스(`License_2_0`, `EsnecilReader_*`), K8s 모니터링(`K8SManager` 등), 알럿 발송(`ITSMAPISender`), 캐시/카운터/GeoIP/UserAgent, HTTP 프로세서까지 시스템의 거의 모든 레이어가 이 클래스를 직접 참조합니다. 커뮤니티 탐지로 패키지 경계를 나눠도 이 노드는 그 경계를 전부 가로지르는 브릿지로 남습니다 — 즉 설정 필드 하나를 바꾸는 변경이라도 전체 코드베이스에 영향을 줄 수 있다는 뜻입니다.
- **`tuna.server.db.common.elastic.ConnectionManager`** (68 edges, 3위) — OpenSearch `RestHighLevelClient` 두 개(read/write)를 들고 있는 싱글턴으로, 생성자에서 `Configure.getInstance().es_server`/`es_port`/`es_max_conn_per_route`/`es_max_total_conn`을 읽어 클라이언트를 구성합니다 (`Configure`에 2-hop으로 직접 의존). `tuna.server.db.rd.elastic`/`db.wr.elastic`의 거의 모든 `*ESRD`/`*ESWR` 클래스가 이 클래스를 통해서만 OpenSearch에 접근합니다.

결합 체인: **DB RD/WR 구현체 → `ConnectionManager` → `Configure`**. `I*RD`/`I*WR` + Factory 패턴이 백엔드 교체를 위한 간접화를 표방하지만, 실제로는 모든 구현체가 `ConnectionManager`라는 단일 커넥션 소스에 하드하게 묶여 있습니다. ES 클러스터 구성이나 커넥션 정책을 바꿀 때는 이 체인에 걸리는 파일 수(수십~백 단위)를 감안해야 합니다.

### 메트릭 수신·처리 파이프라인
애플리케이션 메트릭과 인프라/클라우드 메트릭은 수신 즉시부터 서로 다른 경로를 탑니다.

1. **수신**: `scouter.server.netio.data.net.DataUdpServer`가 UDP로 받은 바이트를 `NetDataProcessor.add(data, addr)` 큐에 넣고, 데몬 워커 스레드들이 소비해서 `Pack`으로 역직렬화합니다 (`main/scala/scouter/server/netio/data/NetDataProcessor.scala`).
2. **타입별 디스패치**: `NetDataProcessor.process(Pack, addr)`의 큰 `match`문이 `Pack.getPackType()`으로 분기합니다.
   - `PERF_COUNTER`/`METRIC` (애플리케이션 성능 메트릭) → `PerfCountCore.add(pack)`
   - `CLOUD_WATCH`/`CLOUD_METRIC`/`STACK_DRIVER`/`CLOUD_RESOURCE` (클라우드 인프라 메트릭) → 각각 `CloudMetricCore`/`CloudMetricsCore`/`CloudMetricGCPCore`/`CloudResourcesCore`
   - `K8S_CONTAINER_PACK`/`K8S_NODE_PACK`/`K8S_NAMESPACE_PACK`/`K8S_WATCH_PACK`/`K8S_EVENT_PACK` (K8s 인프라 상태) → `K8SManager.getInstance().add(pack)`
3. **애플리케이션 메트릭 경로 — `PerfCountCore`** (`main/scala/scouter/server/core/PerfCountCore.scala`): `PlugInManager.counter(pack)`로 `ICounter` 플러그인 통지 → `timetype==REALTIME`이면 `RealtimeCounterESWR`로 즉시 ES 적재 + 카운터별로 `MetricLoopCacheHelper`(대시보드 실시간 캐시) 갱신 + `AlertEngine.putRealTime(...)`으로 실시간 알럿 평가(`RealCounter`가 임계치/평균 계산) + (`alert_ai_use`면) `AiAlertManager.process(...)` + `ObjectCpuChecker.checkCpu(...)` → `timetype==FIVE_MIN`이면 `RealtimeCounter5mESWR`로 5분 집계 적재.
4. **K8s 인프라 경로 — `K8SManager`** (`main/java/scouter/server/core/k8s/K8SManager.java`): 순수 인메모리 모델. `run()` 스레드가 큐를 받아 `K8SClusterStore`의 `K8SContainerMap`/`K8SNodeMap`/`K8SNamespaceMap`/`K8SEventMap`을 갱신만 하고, ES 적재는 이 경로에서 하지 않음 — 대시보드 API(`K8SHandler` 등)가 이 인메모리 맵을 직접 조회.
5. **클라우드 메트릭 경로 — `CloudMetricsCore`** (`main/scala/scouter/server/core/CloudMetricsCore.scala`): 원래는 `PlugInManager.cloudMetric(pack)`으로 플러그인(알럿 등)에 통지 후 ES 적재였으나, 현재 그 플러그인 통지 라인이 주석 처리되어 있고 `cloudMetricsWr.add(pack)`로 **ES 적재만** 수행 (아래 "클라우드 메트릭/알럿 처리 방향 전환" 참고).

```
Agent(App/Cloud/K8s) --UDP--> DataUdpServer --> NetDataProcessor.process(Pack)
                                                     │
                       ┌─────────────────────────────┼───────────────────────────────┐
                       ▼                              ▼                               ▼
              PerfCountCore (App 메트릭)       CloudMetricsCore 등 (클라우드)      K8SManager (K8s)
                       │                              │                               │
        PlugInManager.counter()           (플러그인 통지 - 현재 비활성)        인메모리 클러스터 상태 갱신
        AlertEngine.putRealTime() (실시간 알럿)        │                               │
        MetricLoopCache (대시보드 캐시)                ▼                               ▼
                       │                    CloudMetricsESWR (ES 즉시 적재)   K8SObjectESWR 등 (별도 flush)
                       ▼
        RealtimeCounterESWR / RealtimeCounter5mESWR (ES 적재)
```

<details>
<summary>핵심 코드 스니펫 (클릭해서 펼치기)</summary>

**① 타입별 디스패치** — `main/scala/scouter/server/netio/data/NetDataProcessor.scala` (`Pack.getPackType()`으로 분기하는 부분만 추림)

```scala
def process(p: Pack, addr: InetAddress) {
    if (p == null) return
    p.getPackType() match {
        case PackEnum.PERF_COUNTER =>
            val counterPack = p.asInstanceOf[PerfCounterPack]
            if (counterPack.time == 0) counterPack.time = System.currentTimeMillis();
            if (counterPack.timetype == 0) counterPack.timetype = TimeTypeEnum.REALTIME;
            PerfCountCore.add(counterPack)

        case PackEnum.METRIC =>
            val metricPack = p.asInstanceOf[MetricPack]
            if (metricPack.time == 0) metricPack.time = System.currentTimeMillis();
            if (metricPack.timetype == 0) metricPack.timetype = TimeTypeEnum.REALTIME;
            PerfCountCore.add(metricPack)

        case PackEnum.CLOUD_WATCH =>
            CloudMetricCore.add(p.asInstanceOf[CloudWatchPack]);
        case PackEnum.CLOUD_METRIC =>
            CloudMetricsCore.add(p.asInstanceOf[CloudMetricPack])
        case PackEnum.STACK_DRIVER =>
            CloudMetricGCPCore.add(p.asInstanceOf[StackDriverPack])
        case PackEnum.CLOUD_RESOURCE =>
            CloudResourcesCore.add(p.asInstanceOf[CloudResourcePack])

        case PackEnum.K8S_CONTAINER_PACK => K8SManager.getInstance().add(p);
        case PackEnum.K8S_NODE_PACK      => K8SManager.getInstance().add(p)
        case PackEnum.K8S_NAMESPACE_PACK => K8SManager.getInstance().add(p)
        case PackEnum.K8S_WATCH_PACK     => K8SManager.getInstance().add(p)
        case PackEnum.K8S_EVENT_PACK     => K8SManager.getInstance().add(p)
        case PackEnum.K8S_CLUSTER_PACK   => K8SManager.getInstance().newCluster(p.asInstanceOf[K8SClusterPack]);

        case _ => PackExtProcessChain.doChain(p)
    }
}
```

**② 애플리케이션 메트릭 처리** — `main/scala/scouter/server/core/PerfCountCore.scala` (전체)

```scala
object PerfCountCore {
    var queue = new RequestQueue[PerfCounterPack](CoreRun.MAX_QUE_SIZE);
    var counterWr = CounterWRFactory.getCounter()
    var counter5mwr = CounterWRFactory.getCounter5m()
    var metricLoopCacheHelper = MetricLoopCacheHelper.getInstance()
    val conf = Configure.getInstance()
    var perfSystemCounterMetering = PerfSystemCounterMetering.getInstance()
    ThreadScala.startDaemon("scouter.server.core.PerfCountCore", {CoreRun.running}) {
        val counterPack = queue.get();
        var objHash: Int = 0
        if (counterPack.isInstanceOf[MetricPack]) {
            objHash = counterPack.asInstanceOf[MetricPack].objHash
        } else {
            objHash = HashUtil.hash(counterPack.objName);
        }

        PlugInManager.counter(counterPack);

        if (counterPack.timetype == TimeTypeEnum.REALTIME) {
            counterWr.add(counterPack)
            EnumerScala.foreach(counterPack.data.keySet().iterator(), (k: String) => {
                val value = counterPack.data.get(k);
                val counterKey = new CounterKey(objHash, k, counterPack.timetype);
                metricLoopCacheHelper.put(objHash, k, value, counterPack.time)
                AlertEngine.putRealTime(counterKey, value); //experimental
                if (conf.alert_ai_use) {
                    AiAlertManager.getInstance().process(counterKey, value)
                }
            })
            // cpu check and ask generating threaddump if the cpu threshold is exceeded
            ObjectCpuChecker.checkCpu(counterPack)

        } else if (counterPack.timetype == TimeTypeEnum.FIVE_MIN) {
            var time = System.currentTimeMillis
            time = (time - 10000) / DateUtil.MILLIS_PER_FIVE_MINUTE * DateUtil.MILLIS_PER_FIVE_MINUTE
            counterPack.time = time
            counter5mwr.add(counterPack)
        }
    }

    def add(p: PerfCounterPack) {
        val ok = queue.put(p);
        if (!ok) Logger.println("S109", 10, "queue exceeded!!");
        if (AgentManager.getSystemMap().containsKey(p.objName) && (p.timetype == 1)) {
            perfSystemCounterMetering.add(p)
        }
    }
}
```

**③ K8s 인프라 상태 처리 (인메모리, ES 적재 없음)** — `main/java/scouter/server/core/k8s/K8SManager.java`

```java
public void add(Pack pack) {
    boolean result = objectQueue.put(pack);
    if (!result) {
        Logger.println("J001", 10, "k8s objectQueue is full");
    }
}

public void run() {
    while (CoreRun.running()) {
        try {
            Pack pack = objectQueue.get();
            if (pack instanceof K8SContainerPack) {
                K8SContainerPack containerPack = (K8SContainerPack) pack;
                containerPack.wakeup = System.currentTimeMillis();
                containerPack.alive = true;
                K8SContainerMap containerMap = clusterStore.getContainerMap(containerPack.clusterHash);
                if (containerMap != null) {
                    containerMap.put(containerPack);
                }
            } else if (pack instanceof K8SNodePack) {
                // ... 동일한 패턴으로 K8SNodeMap / K8SNamespaceMap / K8SEventMap 갱신, K8SWatchPack은 K8SCluster.addWatch()
            }
        } catch (Exception e) {
            Logger.println("K20", 10, e);
        }
    }
}
```

**④ 클라우드 메트릭 처리 — "DB를 바라보는 방식"으로 전환된 지점** — `main/scala/scouter/server/core/CloudMetricsCore.scala` (전체)

```scala
object CloudMetricsCore {
    var queue = new RequestQueue[CloudMetricPack](CoreRun.MAX_QUE_SIZE);
    var cloudMetricsWr = CloudMetricsESWR.getInstance()
    ThreadScala.startDaemon("scouter.server.core.CloudMetricsCore", {CoreRun.running}) {
        val CloudMetricPack = queue.get()

        // 2024.11.21
        // Cloud에서 수집되는 metric 데이터는 실시간으로 정확하지 않고, 지연되어 처리되므로
        // CloudMetricPack 처리를 DB 데이터를 바라보면서 처리하기로 업무 방향이 수정되어 이 부분은 주석처리합니다.
//        PlugInManager.cloudMetric(CloudMetricPack)
        cloudMetricsWr.add(CloudMetricPack)
    }

    def add(p: CloudMetricPack) {
        val ok = queue.put(p);
        if (!ok) {
            Logger.println("S513", 10, "queue exceeded!!");
        }
    }
}
```

</details>

### UDP 수신 상세 흐름 (`DataUdpServer` → `NetDataProcessor`, graphify로 확인됨)
위 파이프라인의 "①수신" 단계를 클래스 로딩부터 워커 스레드까지 펼쳐보면 **수신 스레드(1개)와 파싱/디스패치 스레드(N개)가 큐로 분리**되어 있습니다.

- **`DataUdpServer`** (`main/scala/scouter/server/netio/data/net/DataUdpServer.scala`) — `object` 로딩 시 데몬 스레드가 자동 기동되어 `while(true) { open(); recv(); close() }`를 반복(자기복구 루프).
  - `open(host, port)` — `DatagramSocket` 바인딩. 실패하면 로그(`S157`) 남기고 3초 대기 후 재시도.
  - `recv()` — `udpsocket.receive(p)` 블로킹 루프로 패킷을 받아 **`NetDataProcessor.add(data, addr)`**로 넘김. 예외가 나면 로그(`S151`) 남기고 메서드 종료 → 바깥 루프가 소켓을 닫고 `open()`부터 재시작.
- **`NetDataProcessor`** (`main/scala/scouter/server/netio/data/NetDataProcessor.scala`) — `add()`는 바이트를 즉시 파싱하지 않고 `RequestQueue[NetData]`(`conf.net_queue_size`)에 적재만 함(가득 차면 드롭 + `S158` 로그). `_net_udp_worker_thread_count`개의 데몬 워커 스레드가 `queue.get()`으로 소비해 `process(NetData)` 호출.
  - `process(NetData)`: 앞 4바이트(`cafe` 코드)로 프로토콜 변종 판별 — 단일 팩(`UDP_CAFE`/`UDP_JAVA`) → `processCafe`, 배치(`..._N`) → `processCafeN`, MTU 초과 분할(`..._MTU`) → `processCafeMTU`(`MultiPacketProcessor`로 조각 재조립 후 완성되면 재귀적으로 `process` 호출). 각 경로는 `DataInputX.readPack()`으로 `Pack`을 역직렬화해 `process(Pack, addr)` 호출 — 이것이 CLAUDE.md 상단의 "타입별 디스패치" 큰 `match`문.

```
[클래스로딩 시] DataUdpServer 데몬 스레드 자동 시작
        │
        ▼
open(host, port)  ──(바인딩 실패)──▶ 3초 대기 후 재시도
        │ (성공)
        ▼
recv()  : udpsocket.receive(p) 블로킹 루프
        │  받은 바이트 복사
        ▼
NetDataProcessor.add(data, addr)
        │  RequestQueue에 적재 (가득 차면 드롭+로그)
        ▼
[워커 스레드 N개] queue.get() → process(NetData)
        │  cafe 코드로 단일/배치/분할 판별 → readPack()
        ▼
process(Pack, addr)  — Pack 타입별 큰 match문 (위 "타입별 디스패치" 참고)
```

### PerfCountCore 상세 로직 (graphify로 확인됨)
위 파이프라인 "③ 애플리케이션 메트릭 경로"를 펼쳐보면, `add()`(인입)와 데몬 루프(소비) 두 축이며, REALTIME 한 틱마다 서로 독립적인 다섯 갈래(ES 적재/실시간 캐시/룰 알럿/AI 알럿/CPU 진단)로 위임됩니다. 하나가 실패해도 나머지에 영향 없습니다.

- **`add(p)`** (`main/scala/scouter/server/core/PerfCountCore.scala:86`) — `NetDataProcessor`가 넘긴 팩을 큐에 적재만 하고 즉시 리턴. 추가로 **`AgentManager.getSystemMap()`에 objName이 등록돼 있고 REALTIME이면** 별도로 `PerfSystemCounterMetering`(`main/java/scouter/server/core/PerfSystemCounterMetering.java`)에도 복제 전달 — 시스템(그룹) 단위 집계용 사이드 경로로, `tuna.server.counter.CounterManager`의 `MeterPerf`에 `agg`(avg/sum/sum_ped) 방식으로 누적됨.
- **데몬 루프 — REALTIME 분기**: `PlugInManager.counter(pack)`(ICounter 플러그인 통지) → `counterWr.add(pack)`(`RealtimeCounterESWR`로 ES 즉시 적재) → 카운터별 foreach에서 `metricLoopCacheHelper.put()` + `AlertEngine.putRealTime()` + (`alert_ai_use`면) `AiAlertManager.process()` → `ObjectCpuChecker.checkCpu(pack)`.
  - **`MetricLoopCacheHelper`/`MetricLoopCache`** (`main/java/scouter/server/core/cache/metric/`) — `(objHash, counterKey)`별 고정 크기(100) **원형 버퍼**. `getList()`(구간 조회, 5분 초과 데이터 자동 제외)와 `getLast()`(최근 10초 이내 값)를 제공하며, 대시보드 실시간 위젯(`DashboardNumberWidgetImpl`, `CustomDashboardProcessorImpl`, `GeneralProcessorImpl` 등 다수의 `*ProcessorImpl`)이 이 캐시를 직접 조회. `AiAlertManager.processAnomalyDetect()`도 여기서 두 카운터의 최근 시계열을 꺼내 상관계수를 계산.
  - **`AiAlertManager`** (`main/java/tuna/server/manager/AiAlertManager.java`) — 룰 스크립트가 아니라 **DB에서 미리 계산해둔 동적 임계치(`DynamicThreshold`, `AiOpsConfigRDFactory`로 로드)**로 판단하는 별도 AI 알럿 경로. `objHash:counter` 키로 상/하한 초과 확인 → 카운터가 `"55S"`면 `checkCountPerPeriod()`로 "일정 기간 내 N회 이상 초과"까지 추가 확인 → `sendAlert()`에서 `objHash+title` 기준 `alert_send_interval_ms` 이내 중복 발송 억제 후 `AlertCore.add()`(룰 엔진의 `silentTime`과 유사하지만 별도 구현). `processAnomalyDetect()`(다른 경로에서 호출)는 두 카운터의 `MetricLoopCache` 시계열로 피어슨 상관계수를 계산해 저장된 `coEfficientMap`과 `alert_corr_diff` 이상 벌어지면 "Anomaly Detect" 알럿 발생.
  - **`ObjectCpuChecker`** (`main/scala/scouter/server/core/app/ObjectCpuChecker.scala`) — `objType`이 `FAMILY_JAVAEE`인 에이전트만 대상. 태그(`TAG_AUTODUMP_CPU_ENABLED/_DURATION/_THRESHOLD`)로 활성화 여부/기준을 읽고, `PROC_CPU` 카운터가 threshold를 초과하는 상태가 `duration`만큼 지속되면 `AgentCall.call(..., TRIGGER_THREAD_DUMPS_FROM_CONDITIONS, ...)`로 **에이전트에게 스레드덤프를 원격 트리거**(별도 큐+데몬 스레드로 비동기 처리).
- **데몬 루프 — FIVE_MIN 분기**: 카운터별 foreach 없이 팩 전체를 5분 버킷으로 스냅해 `counter5mwr`(`RealtimeCounter5mESWR`)에 그대로 적재만 함 — 알럿/캐시/AI 경로 전부 스킵.

```
NetDataProcessor.process() [PERF_COUNTER/METRIC]
        │
        ▼
PerfCountCore.add(pack) ──(systemMap에 등록된 objName + REALTIME)──▶ PerfSystemCounterMetering (시스템 그룹 집계, 별도 경로)
        │  RequestQueue
        ▼
[데몬 스레드] queue.get()
        │
        ├─ PlugInManager.counter(pack)                         → ICounter 플러그인 통지
        │
        ├─ REALTIME ─┬─ counterWr.add(pack)                    → RealtimeCounterESWR (ES 즉시 적재)
        │             ├─ (카운터별) metricLoopCacheHelper.put() → MetricLoopCache 원형버퍼 (대시보드/AI가 조회)
        │             ├─ (카운터별) AlertEngine.putRealTime()   → 룰 기반 알럿 (AlertRule/.alert 스크립트)
        │             ├─ (카운터별, alert_ai_use) AiAlertManager.process() → 동적 임계치 알럿 (DB 기반, DynamicThreshold)
        │             └─ ObjectCpuChecker.checkCpu(pack)        → CPU 초과 지속 시 에이전트에 스레드덤프 트리거
        │
        └─ FIVE_MIN ── counter5mwr.add(pack)                    → RealtimeCounter5mESWR (5분 집계 적재, 알럿/캐시 없음)
```

## 현재 브랜치 참고 (`feature/PJ190332-2973-cloud-alert`)

`pom.xml`에는 로컬 테스트용으로 주석 처리된 `tuna.server.plugin.cloud.alert`, `tuna.server.plugin.slack` 의존성이 있습니다 (주석에 "운영 이관 전에 제거해주세요"라고 명시되어 있음 — 실제 배포 전 반드시 확인). `profile/local/conf`에는 이에 대응하는 `cloud_alert_plugin.conf`, `plugin_slack.yaml` 로컬 테스트 설정 파일이 있습니다.

### 클라우드 메트릭/알럿 처리 방향 전환 (git 히스토리로 확인됨)
- `CloudMetricsCore.scala`(클라우드 메트릭 수신 처리)에는 "Cloud에서 수집되는 metric 데이터는 실시간으로 정확하지 않고 지연되어 처리되므로 CloudMetricPack 처리를 DB 데이터를 바라보면서 처리하기로 업무 방향이 수정되어 이 부분은 주석처리합니다"라는 주석과 함께 `PlugInManager.cloudMetric(pack)` 호출이 커밋 `a4b5e641`(PJ190332-3021)에서 주석 처리됨. 즉 클라우드 메트릭은 이제 실시간 스트림이 아니라 **ES에 적재된 데이터를 별도로 조회**하는 방식으로 알럿을 판단하는 방향으로 바뀜.
- **Cloud Alert Rule을 DB에서 읽는 로직은 한 번 서버에 구현됐다가 완전히 제거되어 외부 플러그인으로 이전됨**:
  1. `1bcf8857`(PJ190332-2988) — `tuna.server.manager.OpenSearchCacheScheduler` 추가: 5초마다 OpenSearch `cloud-alert-rule` 인덱스(`activeYn=Y` 필터)를 조회해서 메모리에 캐싱하는 스레드.
  2. `ef74072d`(PJ190332-2988, 바로 다음날) — 커밋 메시지 "Cloud Alert Rule 캐싱 기능을 플러그인으로 옮기면서 TunA Server에서 제거함"대로 `OpenSearchCacheScheduler`와 테스트, `Main.java` 호출부를 통째로 삭제.
  3. 이후 커밋들(`1254d4eb`, `32ca046e`~`76cc3c09`, `f40f9e3d`, `3e365a02`)은 그 자리를 대체하는 **외부 플러그인 모듈 `tuna.server.plugin.cloud.alert`**를 로컬에서 테스트하기 위한 설정(`cloud_alert_plugin.conf`)과 테스트 코드(`NetDataProcessorTest_CloudMetricPack.java`, `plugin.cloud.alert.config` 시스템 프로퍼티 설정)만 추가.
- 결론: 현재 이 리포의 `src/`에는 Cloud Alert Rule을 DB에서 읽거나 평가하는 로직이 **없습니다**. `tuna.server.db.rd`에도 `ICloudMetricRD`/`CloudMetricESRD` 같은 읽기 인터페이스가 없어(WR만 존재) 이를 뒷받침합니다. 실제 규칙 조회/평가는 별도 아티팩트인 `tuna.server.plugin.cloud.alert` 플러그인 쪽에 있고, 이 리포는 그 플러그인을 로드하기 위한 설정 배포/로컬 테스트 지점만 가지고 있습니다.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
