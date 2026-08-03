# tuna.agent.cloud

클라우드 인프라 위(또는 인접한 곳)에서 실행되는 Go 데몬으로, AWS(그리고 부분적으로 GCP) API — CloudWatch, EC2, Lambda, Kafka, X-Ray, 리소스 태그 — 를 폴링해 메트릭을 수집하고, Scouter UDP 프로토콜(`scouter-go-lib`)을 통해 **TunA 수집서버(collector server)** 로 전송합니다. 

TunA 서버가 설정을 푸시하거나 에이전트/Clipper 상태를 조회할 수 있도록 로컬 HTTP API도 함께 제공합니다.

모듈명: `tuna.io/agent/tuna.agent.cloud`

## 요구 사항

- Go 1.18+
- AWS 자격증명(수집 대상 계정에 대한 CloudWatch/EC2/Lambda/Kafka/X-Ray/ResourceGroupsTaggingAPI 조회 권한)
- 접근 가능한 TunA 수집서버(UDP + HTTP 엔드포인트)

`go.mod`에 `replace github.com/scouter-project/scouter-go-lib v0.2.0 => ../scouter-go-lib`가 있지만 실제 사용 버전은 `v0.4.0`(go.sum에 정상 존재)이라 이 replace는 적용되지 않으며, 빌드에 `../scouter-go-lib` 형제 저장소는 필요하지 않습니다.

## 빌드

```bash
# 리눅스용 빌드 (./target/tuna.agent.cloud/ 에 바이너리 + tarball 생성)
./build.sh

# 윈도우용 빌드 (.\target\tuna.agent.cloud\ 에 .exe + zip 생성)
build.bat

# 패키징 없이 즉석 빌드
go build -buildvcs=false -o tuna.agent.cloud app.go
```

## 실행

앱 홈 디렉토리(`-app.home` 플래그로 지정, 미지정 시 자동 탐지) 아래에 다음이 필요합니다.

- `conf/tuna.conf` — 필수 설정 파일 (예시: [home/conf/tuna.conf](home/conf/tuna.conf))
- `logs/` — 로그 출력 디렉토리

```bash
./tuna.agent.cloud start        # 시작
./tuna.agent.cloud start force  # force 모드로 시작
./tuna.agent.cloud stop         # 중지
```

`home/start.sh` / `home/stop.sh`는 앱 홈 디렉토리 안의 `<pid>.tuna` 마커 파일로 프로세스를 관리합니다. 실제 pidfile 서브시스템이 아니라, `app.go`가 이 파일의 존재 여부를 폴링해서 언제 프로세스를 종료할지 결정하는 방식입니다.

### 주요 설정 항목 (`conf/tuna.conf`)

| 키 | 설명 |
| --- | --- |
| `net_collector_ip` / `net_collector_udp_port` / `net_collector_tcp_port` | TunA 수집서버 주소 및 포트 |
| `net_collector_http_port` | 클라우드 설정 조회에 사용하는 수집서버 HTTP 포트 |
| `net_collector_connect_retry` | 설정 조회 실패 시 재시도 간격(초) |
| `api_server_port` | 이 에이전트가 노출하는 로컬 HTTP API 포트 |
| `clipper_name` | 이 에이전트 인스턴스를 식별하는 이름 |
| `trace_*` | 각 영역(heartbeat, http body, metric, resource, xray 등)별 상세 로깅 스위치 |
| `aws_metrics_api` | 사용할 AWS 메트릭 조회 API (예: `GetMetricData`) |

### 설정(`conf/tuna.conf`) 수정이 실행 중인 프로세스에 반영되는 시점

이 값들이 다시 읽히는 코드 위치에 따라 반영 시점이 다릅니다. `scouter-go-lib`의 `Configure`가 파일을 실제로 재로드하는지는 외부 라이브러리 영역이라 이 저장소 코드로는 확정할 수 없고, 아래는 "이 값을 다시 읽어오는 코드가 언제 실행되는가"를 기준으로 정리한 것입니다.

1. **재시작해야만 반영**: 프로세스 생애주기 중 딱 한 번만 읽습니다.
   - `api_server_port`, `api_server_write_timeout`, `api_server_read_timeout`, `api_server_idle_timeout` — `apiserver/server.go`의 `StartAPIServer()`(프로세스 시작 시 1회)
   - `log_dir` — `app.go`의 `main()` 시작 시
   - `net_collector_ip`, `net_collector_udp_port`, `net_udp_max_bytes`(클라이언트 초기화용) — `netio/udpsender/udpsender.go`의 `UDPSender.GetInstance()`(`sync.Once`)
   - `clipper_name` (없으면 hostname+`api_server_port`로 생성) — `task/agent_heartbeat.go`의 `ApplyObjectInfo()`
   - `aws_xray_collect_interval_sec` — XRayClipper 생성 시점에 필드로 고정

2. **특정 트리거가 있어야 반영** (재시작 불필요): `StartClippersWithCloudConfiguration()`이 다시 호출될 때만 읽습니다. 트리거는 ① TunA 서버의 `POST /api/v1/config` 콜백(즉시), ② 30분 주기 `PeriodicallyHealthCheck()`(최대 30분 지연), ③ 재시작 중 하나입니다.
   - `net_collector_ip`, `net_collector_http_port`, `net_collector_connect_retry`, `trace_http_body` — `agent/agent_server.go`
   - `aws_metrics_api` — `clipper/aws/handler/handler.go`의 `StartClippers()`(위 트리거로 clipper가 전부 재생성될 때만 다시 읽음)

3. **다음 수집 루프/heartbeat tick에서 바로 반영** (가장 빠름): 각 Clipper의 `Clip()` 반복문이나 heartbeat 10초 tick 안에서 매번 다시 읽습니다.
   - `trace_metric`, `trace_metric_list`, `trace_metric_result`, `trace_metric_pack`, `trace_resource`, `trace_xray`, `debug_xray`, `trace_heartbeat`
   - `net_udp_max_bytes` — `netio/udpsender/udpsender.go`의 `send()`에서도 전송 시점마다 별도로 재조회

즉 conf 파일만 고쳐놓고 아무 것도 하지 않으면, 2번 그룹 값은 최대 30분 뒤에나 반영되고 즉시 반영하려면 `/api/v1/config`를 호출해야 하며, 1번 그룹 값은 재시작 전까지 절대 반영되지 않습니다.

## 테스트

```bash
go test ./...
go test ./clipper/aws/aws_kafka/...      # 특정 패키지
go test ./apiserver/... -run TestAWSGetServiceGraphHandler  # 단일 테스트
```

주의:
- `apiserver/handlers_test.go`의 `TestAWSGetServiceGraphHandler`는 `localhost:8800`으로 실제 HTTP 요청을 보내므로 이 에이전트가 실행 중이어야 하며, 격리된 유닛 테스트가 아닙니다.
- `app_test.go`의 `TestApp`은 특정 개발 환경의 로컬 경로를 하드코딩하고 `main()`을 무한루프로 호출하므로 CI/다른 머신에서 그대로 실행할 목적이 아닙니다.

## 아키텍처 개요

**진입점 (`app.go`)**: `conf/tuna.conf`를 로드하고 `<pid>.tuna` 마커를 기록한 뒤, 아래 네 구성요소를 동시에 기동하고 마커 파일이 삭제될 때까지(`stop` 실행 시) 블로킹합니다.

1. `udpsender.GetInstance()` — Scouter `Pack`을 배치로 모아 수집서버로 스트리밍하는 싱글톤 UDP 클라이언트.
2. `startClippers()` — 에이전트 신원 정보를 10초 주기로 하트비트 전송하고, 초기 클라우드 설정을 가져와 Clipper를 기동.
3. `startApiServer()` — TunA 서버가 이 에이전트를 콜백할 수 있는 로컬 HTTP 서버(gorilla/mux).
4. `agent.PeriodicallyHealthCheck()` — 30분 주기로 클라우드 설정 조회를 재실행.

```
main() [app.go:L24]
  |
  +-- conf/tuna.conf 로드
  |
  +-- writePid()  ---------------------------+
  |     <pid>.tuna 생성                       |
  |                                           |
  +-- udpsender.GetInstance()                 |
  |     (UDP 클라이언트 싱글톤, 내부 goroutine)   |
  |                                           |
  +-- startClippers()                         |
  |     heartbeat(10s) + 최초 설정 조회          |
  |                                           |
  +-- startApiServer()                        |
  |     로컬 HTTP 서버 (goroutine)              |
  |                                           |
  +-- startRemoteHealthCheck()                |
  |     PeriodicallyHealthCheck (goroutine)    |
  |                                           |
  +-- for fileExist(pidfile) { sleep(1s) } <---+
        |
        | pid 파일 삭제(stop()) 시 탈출
        v
      main() 종료
```

```go
// app.go
udpsender.GetInstance()
startClippers()
startApiServer()
startRemoteHealthCheck()

for fileExist(pidfile) {
    time.Sleep(1 * time.Second)
}
```

**설정 조회/diff 루프 (`agent/agent_server.go`)**: 수집서버 HTTP 엔드포인트로 `L06` 타입 요청을 POST하고, 실패 시 재시도합니다. 응답 원문을 마지막으로 본 텍스트와 비교해 불필요한 재시작을 피하며, 조회 실패나 빈 설정이면 Clipper를 정지하고 그렇지 않으면 재기동합니다.

```
[app.go / CloudConfigChangeHandler / HealthCheck]
        |
        v
StartClippersWithCloudConfiguration()
        |
        v
  +------------------------------+
  | retry loop (최대 N회)          |
  |  POST /api {reqType:"L06"} -----> TunA 수집서버
  |  실패 시 10초 대기 후 재시도      |
  +------------------------------+
        |
        +--- 응답 없음 ------------------> StopClippers()
        |                                 SetOriginalText("")
        |
        v (응답 있음)
  이전 원문 == 이번 원문 ?
    |            |
   YES           NO
    |            |
    v            v
  아무 것도     csp=="AWS" credential만 추출
  안 함              |
                     v
              awsConfigList 비었나?
                 |         |
                YES        NO
                 |         |
                 v         v
           StopClippers()  StartClippers(awsConfigList)
```

```go
// agent/agent_server.go: StartClippersWithCloudConfiguration()
url := fmt.Sprintf("http://%s:%d%s",
    conf.ReadStringValue("net_collector_ip", "127.0.0.1"),
    conf.ReadIntValue("net_collector_http_port", 6180),
    apiclient.RequestConfigEndPoints)

m := ConfigRequest{ReqType: requestType, ObjHash: []int32{task.GetObjHash()}}
jsonStr, _ := json.Marshal(m)

var res *http.Response
retryCnt := 0
for !configurationReceived && retryCnt < conf.ReadIntValue("net_collector_connect_retry", 10) {
    req, err := http.NewRequest("POST", url, bytes.NewBuffer(jsonStr))
    client := &http.Client{Timeout: time.Second * 10}
    res, err = client.Do(req)
    if err != nil {
        time.Sleep(10 * time.Second)
        retryCnt += 1
        continue
    }
    configurationReceived = true
}

// 재시도를 다 써도 응답이 없으면 clipper 전부 정지
if !configurationReceived {
    aws_clippers_handler.StopClippers()
    configurationTextManager.SetOriginalText("")
    return
}

// ... responseBody 를 읽어서 map[string][]any 로 언마샬링 ...

if configurationList, found := responseMap[requestType]; found {
    // 이전에 저장해둔 원문과 이번 응답 원문이 다를 때만 재구성
    if *configurationTextManager.GetOriginalText() != string(responseBody) {
        for _, singleConfiguration := range configurationList {
            for _, credential := range singleConfiguration.(map[string]any)["credentials"].([]any) {
                switch credential.(map[string]any)["csp"] {
                case aws_common.CSP_NAME:
                    bytes, _ := json.Marshal(singleConfiguration)
                    var awsCloudConfiguration aws_common.AWSCloudConfiguration
                    if err = json.Unmarshal(bytes, &awsCloudConfiguration); err != nil {
                        continue
                    }
                    awsConfigList = append(awsConfigList, awsCloudConfiguration)
                }
            }
        }
    }

    if len(awsConfigList) > 0 {
        aws_clippers_handler.StartClippers(awsConfigList)
    }
    if len(awsConfigList) == 0 {
        aws_clippers_handler.StopClippers()
    }
}
```

**Clipper 플러그인 모델**: `clipper.IClipper`(`Clip()` / `Stop()` / `GetStatus()`)가 핵심 인터페이스이며, `clipper/clipper_manager`의 두 싱글톤 레지스트리(`ClipperManager`: CloudWatch/Resource용, `XRayClipperManager`: X-Ray용)가 이름(`<시작타임스탬프>|<configName>|<credentialName>|<region>|<clipperType>`)을 키로 관리합니다.

```
              +----------------------------+
              |   IClipper (인터페이스)       |
              |  Clip() / Stop() / GetStatus()|
              +----------------------------+
                 ^   ^   ^    ^    ^    ^
                 |   |   |    |    |    |
     +-----------+   |   |    |    |    +-----------+
     |               |   |    |    |               |
CloudWatchClipper  Resource  EC2  Kafka  Lambda  XRayClipper
                    Clipper Clipper Clipper Clipper

  +---------------------+        +------------------------+
  |    ClipperManager    |        |   XRayClipperManager    |
  |  map[name]IClipper   |        |  map[account]IClipper   |
  |  (mutex 보호)          |        |  (X-Ray 전용)             |
  |  Put/Get/Remove      |        |  Put/Get               |
  +---------------------+        +------------------------+
   관리: CloudWatch, Resource        관리: XRayClipper
```

```go
// clipper/iclipper.go
type IClipper interface {
    Clip()
    Stop()
    GetStatus() common.AgentStatus
}
```

Clipper 이름은 `strings.Split`으로 다시 잘라서 위치(index)로 필드를 구분하는 암묵적 계약을 사용합니다 (`apiserver/handlers.go`의 `CloudConfigStatusHandler` 참고):

```
[생성: clipper/aws/handler/handler.go]

  stTime + "|" + configName + "|" + credentialName + "|" + region + "|" + 타입
    |
    v
  "0803143022|myConfig|myCred|ap-northeast-2|CloudWatch"


[파싱: apiserver/handlers.go CloudConfigStatusHandler]

  strings.Split(name, "|")
        |
        v
  [0]=타임스탬프  [1]=configName  [2]=credentialName  [3]=region  [4]=타입
                     ^                                                ^
                     |                                                |
              요청받은 configName과 비교                        "CloudWatch" 여부 비교
```

```go
// clipper/aws/handler/handler.go (생성)
now := time.Now()
stTime := fmt.Sprintf("%02d%02d%02d%02d%02d", now.Month(), now.Day(), now.Hour(), now.Minute(), now.Second())
clipper_basename := stTime + STR_SEPERATOR + tunaConfig.Name + STR_SEPERATOR + cred.Name + STR_SEPERATOR + region

cloudwatchClipper := &aws_cloudwatch.CloudWatchClipper{
    Name: clipper_basename + STR_SEPERATOR + "CloudWatch", // 예: "0803143022|myConfig|myCred|ap-northeast-2|CloudWatch"
    // ...
}
resourceClipper := &aws_resource.ResourceClipper{
    Name: clipper_basename + STR_SEPERATOR + "Resource",
    // ...
}
```

```go
// apiserver/handlers.go: CloudConfigStatusHandler() (파싱)
clippers := clipper_manager.GetClipperManagerInstance().GetMap()
var msg string
for clipperName, clipper := range *clippers {
    clipperAttr := strings.Split(clipperName, STR_SEPERATOR)
    // [0]=타임스탬프 [1]=configName [2]=credentialName [3]=region [4]=타입

    // agent 상태는 CloudWatch Clipper 상태에만 의존함
    if clipperAttr[1] == configName && clipperAttr[4] == "CloudWatch" {
        resp.AgentID = task.GetObjHash()
        resp.AgentName = task.GetObjName()
        clipperStatus := clipper.GetStatus()
        msg = msg + clipperStatus.Message
        clipperStatus.Message = msg
        resp.AgentStatuses = append(resp.AgentStatuses, clipperStatus)
    }
    if clipperAttr[1] == configName && clipperAttr[4] != "CloudWatch" {
        clipperStatus := clipper.GetStatus()
        msg = msg + clipperStatus.Message
        clipperStatus.Message = msg
    }
}
```

**`clipper/aws/handler/handler.go`**가 AWS용 오케스트레이터로, (config, credential, region) 조합마다 CloudWatchClipper와 ResourceClipper 쌍을 만들어 공유 채널로 통신시키며, 설정 변경 시 전체를 정지 후 재시작합니다.

```
StartClippers(tunaConfigList)
   |
   v
StopClippers()  <-- 기존 clipper 전부 정지+제거
   |
   v
for config in tunaConfigList
  for credential in config.Credentials (csp!=AWS -> skip)
    for region in credential.Region
        |
        +--> 공유 AWS SDK config 생성
        +--> cloudwatchResourceCh 채널 생성
        +--> CloudWatchClipper 생성 (목록에 append만)
        +--> ResourceClipper   생성 (목록에 append만)
   |
   v  (3중 루프 종료 후)
Resource Clipper 전부 goroutine 시작 (GatherResourceTags -> Clip)
   |
   v
sleep(2초)
   |
   v
CloudWatch Clipper 전부 goroutine 시작 (Clip)
```

```go
// clipper/aws/handler/handler.go
func StartClippers(tunaConfigList []aws_common.AWSCloudConfiguration) {
    StopClippers() // 항상 먼저 기존 clipper 전부 정지+제거

    var cloudwatchClippers []aws_cloudwatch.CloudWatchClipper
    var resourceClippers []aws_resource.ResourceClipper

    for _, tunaConfig := range tunaConfigList {
        if len(tunaConfig.Metrics) == 0 || tunaConfig.Use == "N" {
            continue // 수집할 메트릭이 없거나 비활성화된 설정은 건너뜀
        }
        for _, cred := range tunaConfig.Credentials {
            if cred.Csp != aws_common.CSP_NAME {
                continue
            }
            for _, region := range cred.Region {
                // CloudWatch 클리퍼와 Resource 클리퍼가 통신할 채널 (버퍼 100)
                cloudwatchResourceCh := make(chan aws_cloudwatch.AWSMetric, 100)

                // 서비스 유형에 관계없이 같은 세션을 공유
                awsConfig, _ := config.LoadDefaultConfig(context.TODO())
                awsConfig.Region = region
                awsConfig.Credentials = aws.NewCredentialsCache(
                    credentials.NewStaticCredentialsProvider(cred.AccessKeyID, cred.SecretAccessKey, ""))

                cloudwatchClipper := &aws_cloudwatch.CloudWatchClipper{
                    Name:                 clipper_basename + STR_SEPERATOR + "CloudWatch",
                    Metrics:              make(map[int64]aws_cloudwatch.AWSMetric),
                    CloudwatchResourceCh: &cloudwatchResourceCh,
                }
                cloudwatchClipper.SetClient(&awsConfig)
                cloudwatchClippers = append(cloudwatchClippers, *cloudwatchClipper)

                resourceClipper := &aws_resource.ResourceClipper{
                    Name:                 clipper_basename + STR_SEPERATOR + "Resource",
                    CloudwatchResourceCh: &cloudwatchResourceCh, // 같은 채널을 공유
                }
                resourceClipper.SetClient(&awsConfig)
                resourceClippers = append(resourceClippers, *resourceClipper)
            }
        }
    }

    for _, clipper := range resourceClippers {
        go func(c aws_resource.ResourceClipper) {
            c.Running = true
            clipper_manager.GetClipperManagerInstance().PutClipper(c.Name, &c)
            c.GatherResourceTags()
            c.KeepGatherResourceTags()
            c.Clip()
        }(clipper)
    }

    time.Sleep(time.Second * 2)

    for _, clipper := range cloudwatchClippers {
        go func(c aws_cloudwatch.CloudWatchClipper) {
            c.Running = true
            clipper_manager.GetClipperManagerInstance().PutClipper(c.Name, &c)
            c.Clip()
        }(clipper)
    }
}
```

**CloudWatch 메트릭 수집 — API 호출 횟수/시간 최적화 (`clipper/aws/aws_cloudwatch/cloudwatch_clipper.go`)**: AWS CloudWatch API는 한 번에 조회할 수 있는 메트릭 개수에 한도가 있고, 호출이 잦을수록 수집 시간과 비용이 늘어납니다. 이 Clipper는 그 한도 안에서 호출 횟수를 최소화하고, 남은 호출들은 동시에 실행해서 시간을 줄이도록 구현되어 있습니다.

- **`GetMetricData` 경로(기본값)**: `makeGetMetricDataQueryList()`가 메트릭×Dimension×집계유형 조합마다 쿼리를 만들고, `setMetricDataInputList()`가 이 쿼리들을 **API 한도인 500개 단위로 꽉 채워서** `GetMetricDataInput` 배치를 구성합니다. 배치 수(=API 호출 수)를 최소화한 뒤, `clipMetricData()`가 이 배치들을 goroutine + `sync.WaitGroup`으로 **동시에** 호출합니다.
- **`GetMetricStatistics` 경로**: 이 API는 메트릭×Dimension 조합 하나당 호출이 1번씩 필요해 배치가 불가능합니다. 대신 각 조합을 goroutine으로 동시에 호출해 시간만 줄이고, `Throttling: Rate exceeded` 에러가 나면 30분간 전체 동작을 멈추는 백오프를 명시적으로 넣어뒀습니다. 이런 차이 때문에 `aws_metrics_api` 기본값이 배치가 가능한 `GetMetricData`로 설정되어 있습니다.
- **불필요한 호출 자체를 차단**: 수집주기(`CollectIntervalSeconds`)가 지나기 전에는 API를 호출하지 않고, 리소스(Dimension) 목록도 매 루프가 아니라 수집주기(또는 최소 5분)마다만 다시 조회합니다.

```
[GetMetricData 경로]
makeGetMetricDataQueryList()  --> 메트릭 x Dimension x 집계유형 쿼리 생성
        |
        v
setMetricDataInputList()      --> 쿼리를 500개씩 묶어 GetMetricDataInput 배치 구성
        |                         (API 호출 수 최소화)
        v
clipMetricData() 루프
  for batch in vGetMetricDataInputList:
      go func(batch) { clipper.getMetricData(batch) }   <- 배치별로 동시 호출
  wg.Wait()                    (동시 실행으로 소요 시간 최소화)


[GetMetricStatistics 경로 — 배치 불가]
for (metric, dimension) 조합:
    go func() {
        client.GetMetricStatistics(...)
        if err contains "Throttling: Rate exceeded":
            sleep(30분)          <- 한도 초과 시 전체 백오프
    }
wg.Wait()
```

```go
// clipper/aws/aws_cloudwatch/cloudwatch_clipper.go: setMetricDataInputList()
// 하나의 GetMetricDataInput 는 최대 500개 MetricDataQuery 를 가질 수 있음
if i%500 == 0 {
    input = &cloudwatch.GetMetricDataInput{}
    clipper.vGetMetricDataInputList = append(clipper.vGetMetricDataInputList, input)
}
input.MetricDataQueries = append(input.MetricDataQueries, metricDataQuery)

// clipMetricData(): 배치들을 동시에 호출
wg := sync.WaitGroup{}
rLock := sync.Mutex{}
metricDataResults := []types.MetricDataResult{}

for i, input := range clipper.vGetMetricDataInputList {
    wg.Add(1)
    go func(inputId int, endTime time.Time, input *cloudwatch.GetMetricDataInput) {
        defer wg.Done()
        if len(input.MetricDataQueries) == 0 {
            return
        }

        // AWS 메트릭 데이터는 최대 15분 정도 지연된 후에 확인되기도 하므로
        // StartTime 은 EndTime 에서 (수집주기 + 15분)을 뺀 값으로 설정
        startTime := endTime.Add(-1 * time.Duration(clipper.CollectIntervalSeconds+900) * time.Second)
        input.EndTime = &endTime
        input.StartTime = &startTime

        results, err := clipper.getMetricData(input)
        if err != nil {
            clipper.Status = "Broken"
            clipper.StatusMessage = err.Error()
            clipper.Running = false
            return
        }

        rLock.Lock()
        defer rLock.Unlock()
        for _, mResult := range results {
            if mResult.StatusCode == "Complete" && len(mResult.Timestamps) > 0 {
                metricDataResults = append(metricDataResults, mResult)
            }
        }
    }(i, targetEndTime, input)
}
wg.Wait() // 모든 배치의 GetMetricData 호출이 끝날 때까지 대기

if len(metricDataResults) > 0 {
    clipper.processMetricDataResult(metricDataResults) // Pack으로 가공 후 UDP 전송
}

// clipMetricStatistics(): 배치 불가 API의 Throttling 백오프
if strings.Contains(err.Error(), "Throttling: Rate exceeded") {
    logger.Error.Println("Throttling error detected, pausing all operations for 30 minutes.")
    time.Sleep(30 * time.Minute)
}
```

**리소스 정보 수집과 CloudWatch-Resource 채널 (`clipper/aws/aws_resource/resource_clipper.go`)**: CloudWatch API는 메트릭 값과 Dimension(예: `InstanceId=i-xxx`)만 돌려주고, 리소스의 이름/커스텀 태그 같은 메타데이터는 주지 않습니다. 그래서 `resourcegroupstaggingapi`/EC2/Lambda/Kafka 각각의 API로 태그를 채우는 `ResourceClipper`를 별도로 두고, CloudWatchClipper와는 `CloudwatchResourceCh` 채널로만 통신합니다.

- `ResourceClipper`는 두 가지 일을 합니다.
  1. `GatherResourceTags()`/`KeepGatherResourceTags()` — `resourcegroupstaggingapi.GetResources()`(태그 있는 리소스)와 EC2/Lambda/Kafka 각각의 목록 API로 얻은 태그를 합쳐 `allTaggedResources` 맵을 만들고, 수집주기(최소 300초)마다 goroutine으로 반복 갱신.
  2. `Clip()`의 `select` 루프 — 채널로 들어온 메트릭+Dimension 정보에 `allTaggedResources`에서 찾은 태그를 붙여 `CloudResourcePack`으로 UDP 전송. `ResourceLastCheckedTimeList`로 같은 (ResourceId, MetricMetaId) 조합은 수집주기 이내에 중복 전송하지 않음.
- **채널을 둔 이유**:
  1. CloudWatchClipper의 `populateDimensions()`가 측정 대상 Dimension과 `ResourceId`를 이미 계산해두므로, 그 결과를 채널로 그대로 넘겨받아 ResourceClipper가 같은 계산을 중복하지 않게 함(두 Clipper의 ResourceId 계산 방식이 어긋날 위험도 없앰).
  2. 두 Clipper가 서로 다른 주기로 도는 독립된 goroutine이라, 공유 메모리 직접 접근(레이스 컨디션 위험) 대신 Go의 관용적인 채널로만 데이터를 주고받게 함.
  3. `make(chan AWSMetric, 100)` 버퍼로, CloudWatch 쪽이 한꺼번에 여러 메트릭을 채널에 밀어넣어도 ResourceClipper가 블로킹 없이 순서대로 소비하게 함.
  4. 결과적으로 리소스 정보 갱신이 "CloudWatch가 실제로 그 메트릭을 조회하는 시점"에 자연스럽게 동기화되어, 별도 스케줄 동기화 로직 없이도 필요한 시점에만 리소스 정보를 보냄.

```
[CloudWatchClipper]                              [ResourceClipper]
populateDimensions()                             allTaggedResources
  Dimension + ResourceId 계산                       (GatherResourceTags로 주기 갱신)
        |                                                ^
        v                                                |
  CloudwatchResourceCh <-- AWSMetric  ----------->  Clip() select 루프
  (버퍼 100, 채널로만 통신 - 레이스 컨디션 방지)              |
                                                    ResourceLastCheckedTimeList 확인
                                                    (같은 Resource+Metric 중복 전송 방지)
                                                          |
                                                          v
                                                    태그 붙여서 CloudResourcePack 전송
```

```go
// clipper/aws/aws_cloudwatch/cloudwatch_clipper.go: populateDimensions()
// 측정기준(Dimensions)이 포함된 메트릭을 채널을 통해 Resource Clipper 에게 전송
*clipper.CloudwatchResourceCh <- clipper.Metrics[metricMetaId]
```

```go
// clipper/aws/aws_resource/resource_clipper.go: Clip()
for clipper.Running {
    select {
    // 채널을 통해 CloudWatch Clipper 가 보내온 (Dimensions 포함된) 메트릭을 받음
    case metricFromCloudwatch := <-*clipper.CloudwatchResourceCh:
        now := time.Now().UTC().Truncate(time.Minute)

        for _, dimensions := range metricFromCloudwatch.Dimensions {
            resourceId := dimensions.ResourceId
            key := fmt.Sprintf("%d|%d", resourceId, metricFromCloudwatch.MetricMetaId)

            // 이미 확인된 "리소스|메트릭" 조합이고 수집주기가 안 지났으면 다시 안 보냄
            var needReadResource bool = false
            if checkedTime, exists := clipper.ResourceLastCheckedTimeList[key]; exists {
                if int(now.Sub(checkedTime).Seconds()) > clipper.CollectIntervalSeconds {
                    needReadResource = true
                }
            } else {
                needReadResource = true
            }

            if needReadResource {
                clipper.ResourceLastCheckedTimeList[key] = now

                resourcePack := netdata.NewCloudResourcePack()
                resourcePack.Time = now.UnixMilli()
                resourcePack.ResourceID = int64(resourceId)
                resourcePack.MetricMetaID = metricFromCloudwatch.MetricMetaId

                // Dimension 값과, ARN으로 찾은 allTaggedResources 의 태그를 SystemTags/CustomTags 에 채움
                for _, dimension := range dimensions.GetDimensions() {
                    resourcePack.SystemTags.Put(*dimension.Name, *dimension.Value)
                    resourceARN := clipper.getResourceARN(*dimension.Value)
                    rsc := clipper.allTaggedResources[resourceARN]
                    for k, v := range rsc.SystemTags {
                        resourcePack.SystemTags.Put(k, v)
                    }
                }

                clipper.processResourceData(resourcePack) // UDP 전송
            }
        }

    // 1초마다 Running 상태를 확인해서 정지 신호에 반응
    case <-time.After(1 * time.Second):
        if !clipper.Running {
            return
        }
    }
}
```

**CSP 패키지 구성**:
- `clipper/aws/*` — 실제로 운영 중인 유일한 구현체. AWS 관련 작업은 이 트리를 대상으로 합니다.
- `clipper/aws_2021/*` — 컴파일/라우트는 살아있지만(X-Ray 서비스그래프/트레이스 요약 핸들러가 참조) 메인 수집 흐름에는 포함되지 않는 레거시 구현체.
- `clipper/gcp_2021/*` — 메인 흐름에 연결되어 있지 않은 GCP 스캐폴딩. 이 프로젝트는 GCP를 실제로 지원하지 않습니다.

```
[실제 운영 경로]
agent/agent_server.go --> clipper/aws/handler/handler.go --> clipper/aws/*
                                                              (cloudwatch, resource,
                                                               ec2, kafka, lambda)

[레거시지만 컴파일/라우트는 연결됨]
apiserver/handlers.go                 clipper/aws_2021/aws_xray
  AWSGetServiceGraphHandler  ------->  (X-Ray 상태 조회용으로만 사용)
  AWSGetTraceSummaryHandler                  |
                                              X  (참조 안 됨)
                                     clipper/aws_2021/aws_auth
                                     clipper/aws_2021/aws_clipper_manager

[메인 흐름에 미연결]
CredentialCheckHandler
  GCP switch case (빈 스텁)   --X-->  clipper/gcp_2021/*  (호출 경로 없음)
```

```go
// apiserver/handlers.go
import (
    "tuna.io/agent/tuna.agent.cloud/clipper/aws/aws_common"
    aws_common_2021 "tuna.io/agent/tuna.agent.cloud/clipper/aws_2021/aws_common" // 레거시, X-Ray 핸들러에서만 사용
    "tuna.io/agent/tuna.agent.cloud/clipper/aws_2021/aws_xray"                   // 레거시, X-Ray 핸들러에서만 사용
    "tuna.io/agent/tuna.agent.cloud/clipper/gcp_2021/gcp_common"                 // CSP_NAME 상수만 참조, 실제 로직 없음
)

func AWSGetTraceSummaryHandler(w http.ResponseWriter, r *http.Request) {
    var traceSummarySearchArg aws_common_2021.TraceSummarySearchArg
    json.NewDecoder(r.Body).Decode(&traceSummarySearchArg)

    obj := clipper_manager.GetXRayClipperManagerInstance().GetClipper(traceSummarySearchArg.Account)
    clipper := obj.(*aws_xray.XRayClipper)
    traceSummary, err := clipper.GetTraceSummary(&traceSummarySearchArg)
    // ...
}

// CredentialCheckHandler() 의 CSP 분기 - GCP는 빈 스텁, AWS만 실제 검증
func CredentialCheckHandler(w http.ResponseWriter, r *http.Request) {
    var credentialInfo common.Credential
    json.NewDecoder(r.Body).Decode(&credentialInfo)

    switch credentialInfo.Csp {
    case gcp_common.CSP_NAME:
        // 아무 동작 없음 - GCP credential 검증 로직이 구현되어 있지 않음

    case aws_common.CSP_NAME:
        if credentialInfo.Region == "" {
            credentialInfo.Region = "ap-northeast-2"
        }
        option := ec2.Options{
            Credentials: credentials.NewStaticCredentialsProvider(credentialInfo.AccessKeyID, credentialInfo.SecretAccessKey, ""),
            Region:      credentialInfo.Region,
        }
        output, err := ec2.New(option).DescribeRegions(context.TODO(), &ec2.DescribeRegionsInput{AllRegions: &allRegion})
        // ... 리전 목록을 JSON으로 응답
    }
}
```

**로컬 HTTP API (`apiserver/`)**: 라우트는 [apiserver/api-endpoints.go](apiserver/api-endpoints.go)에 정의되고 [apiserver/server.go](apiserver/server.go)에서 gorilla/mux로 등록되며, 핸들러는 [apiserver/handlers.go](apiserver/handlers.go)에 있습니다.

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `POST` | `/api/v1/config` | 새 클라우드 설정 푸시 → Clipper 재시작 트리거 |
| `POST` | `/api/v1/config/status` | Clipper별 `AgentStatus` 조회 (configName으로 필터링) |
| `POST` | `/api/v1/credential/check` | `ec2.DescribeRegions` 호출로 AWS 자격증명 유효성 검사 |
| `-` | `/api/v1/aws/xray/servicegraph`, `/api/v1/aws/xray/tracesummary` | AWS X-Ray 관련 라우트 (레거시 `aws_2021` 트리 사용) |

```
apiserver/server.go 라우팅
  POST /api/v1/config                 --> CloudConfigChangeHandler
                                            --> agent.StartClippersWithCloudConfiguration()

  POST /api/v1/config/status          --> CloudConfigStatusHandler
                                            --> ClipperManager 순회 + 이름 파싱 필터링

  POST /api/v1/credential/check       --> CredentialCheckHandler
                                            --> ec2.DescribeRegions() 즉시 검증

  POST /api/v1/aws/xray/servicegraph  --> AWSGetServiceGraphHandler (aws_2021, 레거시)

  POST /api/v1/aws/xray/tracesummary  --> AWSGetTraceSummaryHandler
                                            --> XRayClipperManager.GetClipper()
                                            --> GetTraceSummary()
```

```go
// apiserver/server.go: StartAPIServer()
port := configure.GetConfigure().ReadStringValue("api_server_port", "8800")

router := mux.NewRouter()

// AWS X-Ray 라우트 (레거시 aws_2021 트리 사용)
router.HandleFunc(awsApiPath, HomeHandler)
subRouterForAWS := router.PathPrefix(awsApiPath).Subrouter()
subRouterForAWS.HandleFunc(awsXRayGetServiceGraphEndPoint, AWSGetServiceGraphHandler).Methods("POST")
subRouterForAWS.HandleFunc(awsXRyaGetTraceSummaryEndPoint, AWSGetTraceSummaryHandler).Methods("POST")

// 설정/자격증명 관련 라우트
router.HandleFunc(ApiPath, HomeHandler)
subRouterForAPI := router.PathPrefix(ApiPath).Subrouter()
subRouterForAPI.HandleFunc(CredentialCheckEndPoint, CredentialCheckHandler).Methods("POST")
subRouterForAPI.HandleFunc(CloudConfigChangeEndPoint, CloudConfigChangeHandler).Methods("POST")
subRouterForAPI.HandleFunc(CloudConfigStatusEndPoint, CloudConfigStatusHandler).Methods("POST")

server = &http.Server{
    Addr:    "0.0.0.0:" + port,
    Handler: router,
}
go func() {
    server.ListenAndServe() // 백그라운드에서 리스닝 시작
}()
```
