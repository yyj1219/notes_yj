# 문제풀어보기

유튜브 [따배씨 CKA 시리즈](https://youtube.com/playlist?list=PLApuRlvrZKojqx9-wIvWP3MPtgy2B372f&si=d2ALVCdH30292TQr)에 CKA 30문제 풀이입니다.  유튜브 영상을 보고 작성한 것으로 일부 오타가 있을 수 있습니다.

## 1. ETCD Backup & Restore

### 문제

```
작업 시스템: k8s-master
First, Create a snapshot of the existing etcd instance running at https://127.0.0.1:2379, saving the snapshot to /data/etcd.snapshot.db.
Next, restore an existing, previous snapshot located at /data/etcd-snapshot-previous.db.

The follwing TLS certificates/key are supplied for conneting to the server with etcdctl:
CA certifidate: /etc/kubernetes/pki/etcd/ca.crt
Client certifidate: /etc/kubernetes/pki/etcd/server.crt
Client key: /etc/kubernetes/pki/etcd/server.key
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `etcd backup`

https://kubernetes.io/docs/tasks/administer-cluster/configure-upgrade-etcd/#snapshot-using-etcdctl-options


### 답안

1. ETCD를 호스팅할 시스템에 ssh 로그인

```sh
ssh k8s-master
```

2. 동작 중인 etcd 버전과 etcdctl 도구 설치여부 확인

```sh
etcd --version
etcdctl version
export ETCDCTL_API=3
```

3. ETCD 백업

https://127.0.0.1:2379에서 실행 중인 기존 etcd 인스턴스의 스냅샷을 /data/etcd.snapshot.db에 생성합니다.

```sh
ETCDCTL_API=3 etcdctl --endpoints=https://127.0.0.1:2379 \
  --cacert=/etc/kubernetes/pki/etcd/ca.crt \
  --cert=/etc/kubernetes/pki/etcd/server.crt \
  --key=/etc/kubernetes/pki/etcd/server.key \
  snapshot save /data/etcd.snapshot.db
```

4. ETCD 복구

기존의 스냅샷 /data/etcd-snapshot-previous.db을 이용해서 etcd를 복구합니다.

```sh
ETCDCTL_API=3 etcdctl snapshot restore --data-dir /var/lib/etcd-previous \
  /data/etcd-snapshot-previous.db
```

5. ETCD 정의 파일 수정

```sh
sudo vi /etc/kubernetes/manifests/etcd.yaml
```

```yaml
...
  - hostPath:
      path: /var/lib/etcd-previous
      type: DirectoryOrCreate
    name: etcd-data
```

6. ETCD 파드 실행 여부 확인

```
sudo crictl ps -a | grep etcd
```

## 2. Pod 생성

### 문제

```
클러스터: k8s
Create a new namespace and create a pod in the namespace.
- namespace name: ecommerce
- pod name: eshop-main
- image: nginx:1.17
- env: DB-mysql
```

### 답안

```sh
kubectl create namespace ecommerce

kubectl run eshop-main --image=nginx:1.17 --env=DB-mysql --namespace=ecommerce
```

## 3. Static Pod 생성

### 문제

```
Configure kubelet hosting to start a pod on the node
- Task:
  - Node: hk8s-w1
  - pod name: web
  - image: nginx
```

### 답안

파드 정의 yaml을 확인합니다.
```sh
kubectl run web --image=nginx --dry-run=client -o yaml
```

파드를 생성할 노드에 ssh로 접속합니다.
```sh
ssh hk8s-w1
```

static Pod를 생성할 위치 확인을 위해 kubelet 프로세스의 config 옵션값을 확인합니다.

```sh
ps -ef | grep kubelet | grep config
```

kubelet 프로세스의 config에서 사용하고 있는 config 파일에서 static Pod의 위치 정보를 확인합니다.
```sh
grep staticPodPath /var/lib/kubelet/config.yaml
```

static Pod를 생성합니다.

```sh
vi /etc/kubernetes/manifest/web.yaml
```

```yaml
apiVersion: v1
kind: Pod
metadata: 
  name: web
spec:
  containers:
  - image: nginx
    name: web
```

## 4. Multi-container Pod 생성

### 문제

```
create pod
- 작업 클러스터: k8s
- create a pod named lab004 with 3 containers running: nginx, redis, memcached
```

### 답안

파드 정의용 yaml 파일을 생성합니다.
```sh
kubectl run lab004 --image=nginx --dry-run=client -o yaml > multi.yaml
```

yaml 파일을 수정해서 multi container 정보를 추가합니다.

```sh
vi multi.yaml
```

```yaml
apiVersion: v1
kind: Pod
metadata: 
  name: lab004
spec:
  containers:
  - image: nginx
    name: nginx
  - image: redis
    name: redis
  - image: memcached
    name: memcached
```

yaml 파일을 반영합니다.

```sh
kubectl apply -f multi.yaml
```

## 5. Side-car Container Pod 생성


### 문제

한 파드 안에 있는 여러 개 컨테이너가 같은 볼륨을 사용하는 환경을 구성하시오.

```
An existing Pod needs to be integrated into the Kubernetes built-in logging architecture (e.g.kubectl logs).
Adding a streaming sidecar container is a good and commmon way to accomplish this requirement.

Task
- Add a sidecar container named sidecar, using the busybox images, to the existing Pod eshop-cart-app.
- The new sidecar container has to run the following command: /bin/sh -c 'tail -n+1 -F /var/log/cart-app-log'
- Use a Volume, mounted at /var/log, to make the log file cart-app.log available to the sidecar container.
- Don't modify the cart-app.
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `sidecar`

https://kubernetes.io/docs/concepts/cluster-administration/logging/#sidecar-container-with-logging-agent

### 답안

실행 중인 파드 eshop-cart-app 정의 yaml 파일을 받습니다.
```sh
kubectl get pods eshop-cart-app -o yaml > eshop-cart-app.yaml
```

eshop-cart-app 정의 yaml 파일을 수정합니다.
```sh
vi eshop-cart-app.yaml
```

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: eshop-cart-app
spec:
  containers:
  - command:
    - /bin/sh
    - -c
    - 'i=0;
      while true;
      do
        echo "$i: $(date)" >> /var/log/1.log;
        echo "$(date) INFO $i" >> /var/log/2.log;
        i=$((i+1));
        sleep 1;
      done'
    image: busybox
    name: cart-app
    volumeMounts:
    - mountPath: /var/log
      name: varlog
  - name: sidecar
    image: busybox
    args: [/bin/sh, -c, 'tail -n+1 -F /var/log/cart-app-log']
    volumeMounts:
    - mountPath: /var/log
      name: varlog
  volumes:
  - emptyDir: {}
    name: varlog
```

작성한 eshop-cart-app 정의 yaml 파일을 반영합니다.
```sh
kubectl apply -f eshop-cart-app.yaml
```

eshop-cart-app 파드에 추가된 sidecar 컨테이너의 로그를 확인합니다.
```sh
kubectl logs eshop-cart-app -c sidecar
```

## 6-1. Deployment & Pod Scale

### 문제

Pod를 Scale out 합니다.

```
작업 클러스터: k8s
Expand the number of running Pods in "eshop-order" to 5.
- namespace: devops
- deployment: eshop-order
```

### 답안

```sh
kubectl scale deployment eshop-order --replicas=5 -n devops
kubectl get deploy -n devops
kubeclt get pods -n devops
```


## 6-2. Deployment & Pod Scale

### 문제

Pod를 Scale out 합니다.

```
작업 클러스터: k8s
- Create a deployment as follows:
- Task:
  - name: webserver
  - 2 replicas
  - label: app_env_state=dev
  - container name: webserver
  - container image: nginx:1.14
- Scale Up Development:
  - Scale the deployment webserver to 3 pods
```

### 답안

디플로이먼트 정의용 기본 yaml 파일을 생성합니다.

```sh
kubectl create deployment webserver --image=nginx:1.14 --port=80 --replicas=2 --dry-run=client -o yaml > webserver.yaml
```

디플로이먼트 정의용 기본 yaml 파일을 수정합니다.
```sh
vi webserver.yaml
```

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: webserver
spec:
  replicas: 2
  selector:
    matchLabels:
      app_env_stage: dev # 레이블로 파드 찾기
  template:
    metadata:
      labels:
        app_env_stage: dev # 파드에 레이블 설정
    spec:
      containers:
      - image: nginx:1.14
        name: webserver
        ports:
        - containerPort: 80
```

yaml 파일을 사용해서 디플로이먼트를 반영합니다.
```sh
kubectl apply -f webserver.yaml
```

디플로이먼트로 배포된 파드들의 레이블에 app_env_state=dev가 포함되어 있는지 확인합니다.
```sh
kubectl get pod --show-labels
```

디플로이먼트를 scale up 합니다.
```sh
kubectl scale deployment webserver --replicas=5
```

## 7. Rolling Update & Roll Back ★

### 문제

```
작업 클러스터: kubectl config use-context k8s
- Create a deployment as follows:
- Task:
  - name: nginx-app
  - Using container nginx with version 1.11.10-alpine
  - The deployment should contain 3 replicas
- Next, deploy the application with new version 1.11.13-alpine, by performing a rolling update
- Finally, rollback that update to the previous version 1.11.10-alpine
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `rollback`

https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#rolling-back-a-deployment

### 답안

컨텍스트를 바꿔줍니다.

```sh
kubectl config use-context k8s
```

디플로이먼트를 생성합니다.
```sh
kubectl create deployment nginx-app --image=nginx:1.11.10-alpine --replicas=3
```

디플로이먼트로 실행된 파드를 확인합니다.
```sh
kubectl get pods | grep nginx-app
```

롤링업데이트를 합니다. (--record 옵션은 생략 가능)
```sh
kubectl set image deployment nginx-app nginx=nginx:1.11.13-alpine --record
```

롤아웃 상태를 확인합니다.
```sh
kubectl rollout status deployment nginx-app
```

롤아웃 이력을 확인합니다.
```sh
kubectl rollout history deployment nginx-app
```

롤백을 합니다.
```sh
kubectl rollout undo deployment nginx-app
```

다시 롤아웃 이력을 확인합니다.
```sh
kubectl rollout history deployment nginx-app
```

## 8. NodeSelector ★

### 문제

Pod를 Schedule 합니다.

```
작업 클러스터: kubectl config use-context k8s
- Schedule a pod as follows:
  - name: eshop-store
  - image: nginx
  - node selector: disktype=ssd
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `assign pod node` 검색 결과 중 두번째 페이지

https://kubernetes.io/docs/tasks/configure-pod-container/assign-pods-nodes/#create-a-pod-that-gets-scheduled-to-your-chosen-node

### 답안

컨텍스트를 바꿔줍니다.
```sh
kubectl config use-context k8s
```

모든 노드의 모든 레이블들을 확인합니다. (=worker 노드의 레이블을 확인합니다.)
```sh
kubectl get node --show-labels
```

disktype 레이블의 값만 확인합니다. disktype 레이블이 있으면 해당 값이 표시되고, 없으면 비어있습니다.
```sh
kubectl get node -L disktype
```

파드 정의 yaml 파일을 만듭니다.
```sh
kubectl run eshop-store --image=nginx --dry-run=client -o yaml > eshop-store.yaml
```

disktype=ssd 레이블을 가지고 있는 노드에서 파드가 실행되도록(=스케줄링되도록) yaml 파일을 수정합니다.
```sh
vi eshop-store.yaml
```

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: eshop-store
spec:
  nodeSelector:
    disktype: ssd
  containers:
  - image: nginx
    name: eshop-store
```

## 9. Node 관리 ★

### 문제

```
작업 노드: k8s-worker1
- Set the node named k8s-worker1 as unavailable and reschedule all the pod running on it
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `drain node`

https://kubernetes.io/docs/tasks/administer-cluster/safely-drain-node/#use-kubectl-drain-to-remove-a-node-from-service

### Node 관리 명령들

특정 노드에서 더 이상 파드가 실행되지 않게 하기 = 스케줄링 중지
```sh
kubectl cordon <노드이름>
```

특정 노드에서 다시 파드가 실행되게 하기 = 스케줄링 다시 시작
```sh
kubectl uncordon <노드이름>
```

특정 노드에 있는 모든 파드를 삭제하고, 다른 노드로 파드들을 이동시키기 = drain = cordon + 기존 파드들 이동
```sh
kubectl drain <노드이름>
```

drain 했던 노드에 다시 파드 배치가 가능하게 하려면 uncordon 합니다. 단, uncordon해도 다른 노드에 있는 파드들이 자동으로 해당 노드에 배치되지는 않습니다.
```sh
kubectl uncordon <노드이름>
```

uncordon한 노드에 다시 파드를 배치시키려면 `kubectl scale --replicas` 명령을 활용해서 파드가 재배치되도록 합니다.
```sh
kubectl scale deployment <디플로이먼트이름? --replicas=<복제개수> -n <네임스페이스>
```

### 참고 - Daemon Set

worker 노드당 한 개씩의 파드만 실행되도록 controller가 제어하는 파드입니다. 주로 cni 제어용 파드나 kube-proxy 등이 여기에 해당합니다.

### 답안

Worker 노드를 drain 합니다.
```sh
kubectl drain k8s-worker1 --ignore-daemonsets --force
```

모든 노드를 확인합니다.
```sh
kubectl get nodes
```

파드를 확인합니다.
```sh
kubectl get pods -o wide
```

## 10-1. Node 정보 수집 ★

### 문제

Ready인 노드 찾기

```
Check to see how many nodes are ready (not including nodes tainted NoSchedule) and write the number to /var/CKA2023/RN0001
```

### 답안

ready 상태인 노드 찾기
```sh
kubectl get nodes | grep -i -w ready
```

찾아낸 노드들 각각의 상세정보에서 taint가 NoSchedule이 아닌 노드만 찾아내기
```sh
kubectl describe node hk8s-m | grep -i taint
kubectl describe node hk8s-w1 | grep -i taint
```

찾아낸 노드 개수를 파일에 쓰기
```sh
echo 1 > /var/CKA2023/RN0001
```

## 10-2. Node 정보 수집

### 문제

Ready인 노드 찾기

```
Determine how many nodes in the cluster are ready to run normal workloads (i.e. workloads that do not have any special tolerations).
Output this number to the file /var/CKA2023/NODE-Count
```

### 답안

```sh
kubectl get nodes | grep -i ready | wc -l > /var/CKA2023/NODE-Count
```

## 11. Deployment & Expose the Service ★

"15. NodePort 서비스 생성"과 함께 알아두면 좋은 문제입니다.

### 문제

```
작업 클러스터: kubectl config use-context k8s
Reconfigure the existing deployment front-end and add a port specification named http exposing port 80/tcp of existing container nginx.
Create a new service named front-end-svc exposing the container port http.
Configure the new service to also expose the individual Pods via a NodePort on the nodes on which they are scheduled.
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `service`

https://kubernetes.io/docs/concepts/services-networking/service/#defining-a-service

### 답안

동작 중인 디플로이먼트 front-end 정의를 yaml 파일로 받습니다.
```sh
kubectl get deploy front-end -o yaml > front-end.yaml
```

받은 yaml 파일을 수정합니다.
```sh
vi front-end.yaml
```
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: front-end
spec:
  replicas: 2
  selector:
    matchLabels:
      run: nginx
  template:
    metadata:
      labels:
        run: nginx
    spec:
      containers:
      - image: nginx
        name: nginx
        ports:
        - containerPort: 80
          name: http # 포트 이름
---
apiVersion: v1
kind: Service
metadata:
  name: front-end-svc
spec:
  type: NodePort
  selector:
    run: nginx
  ports:
  - name: svc-http # 원하는 이름(생략 가능)
    protocol: TCP
    port: 80
    targetPort: http # 파드 정의에서 정한 포트 이름 사용
```

기존 디플로이먼트를 삭제합니다.
```sh
kubectl delete deploy front-end
```

yaml 파일을 반영합니다.
```sh
kubectl apply -f front-end.yaml
```

잘 동작하는지 확인합니다.
```sh
kubectl get deploy front-end
kubectl get svc front-end-svc
```

NodePort로 서비스가 응답하는지 확인합니다.
```sh
curl k8s-worker1:31779
curl <노드의 호스트이름>:<서비스되고 있는 포트>
```

## 12. Pod Log 추출

### 문제

로그에서 몇 라인을 추출해서 기록하기

```
Cluster: kubectl config use-context hk8s
- Monitor the logs of pod custom-app and: Extract log lines corresponding to error 'file not found'. Write then to /var/CKA2023/podlog.
```

### 답안

```sh
kubectl logs custom-app | grep 'file not found' > /var/CKA2022/podlog
```

## 13. CPU 사용량이 높은 Pod 검색

### 문제

```
Cluster: kubectl config use-context hk8s.
From the pod label name=overloaded-cpu, find pods running high CPU workloads and write the name of the pod consuming most CPU to the file /var/CKA2023/cpu_load_pod.txt
```

### 답안

컨텍스트를 설정합니다.
```sh
kubectl config use-context hk8s
```

레이블이 'name=overloaded-cpu'인 파드들의 자원 사용량 보기(CPU를 많이 사용하고 있는 순서로 정렬)
```sh
kubectl top pods -l name=overloaded-cpu --sort-by=cpu
```

확인된 파드이름을 /var/CKA2023/cpu_load_pod.txt 파일에 쓰기
```sh
echo "campus-01" > /var/CKA2023/cpu_load_pod.txt
```

참고 - 리소스 사용량을 보는 top 명령은 노드 수준에서도 사용 가능함
```sh
kubectl top nodes
```

## 14. init 컨테이너를 포함한 Pod 운영

### 문제

init 컨테이너 추가하기

```
Cluster: kubectl conk8s
- Perform the following
- Tasks:
  - Add an init container to web-pod(which has been defined in spec file /data/cka/webpod.yaml).
  - The init container should create an empty file named /workdir/data.txt
  - if /workdir/data.txt is not detected. the Pod shoud exit.
  - Once the spec file has been updated with the init container definition, the Pod shoud be created
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `init container`

파드의 앱 컨테이너들이 실행되기 전에 실행되는 특수한 컨테이너

https://kubernetes.io/docs/concepts/workloads/pods/init-containers/#init-containers-in-use

### 답안

주어진 yaml 파일을 확인합니다.

```sh
cat /data/cka/webpod.yaml
```
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: web-pod
spec:
  containers:
  - image: busybox:1.28
    name: main
    command: ['sh', '-c', 'if [ !-f /workdir/data.txt ];then exit 1;else sleep 300;fi']
    volumeMounts:
    - name: workdir
      mountPath: "/workdir"
  volumes:
  - name: workdir
    emptyDir: {}
```

yaml 파일을 수정합니다.

```sh
cat /data/cka/webpod.yaml
```
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: web-pod
spec:
  containers:
  - image: busybox:1.28
    name: main
    command: ['sh', '-c', 'if [ !-f /workdir/data.txt ];then exit 1;else sleep 300;fi']
    volumeMounts:
    - name: workdir
      mountPath: "/workdir"
  initContainers: # init 컨테이너 추가
  - image: busybox:1.28
    name: init
    command: ['sh', '-c', 'touch /workdir/data.txt']
    volumeMounts:
    - name: workdir
      mountPath: "/workdir"
  volumes:
  - name: workdir
    emptyDir: {}
```

yaml 파일을 적용합니다.
```sh
kubectl apply -f /data/cka/webpod.yaml
```

실행된 파드의 main 컨테이너에서 파일 생성을 확인합니다.
```sh
kubectl exec web-pod -c main -- ls -l /workdir/data.txt
```

## 15. NodePort 서비스 생성

"11. Deployment & Expose the Service"과 함께 알아두면 좋은 문제입니다.

### 문제

```
Set Configuration context $ kubectl config use-context k8s
Create the service as type NodePort with the port 32767 for the nginx pod with the pod selector app: webui
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `nodeport`

https://kubernetes.io/docs/concepts/services-networking/service/#nodeport-custom-port

### 답안

컨텍스트를 적용합니다.
```sh
kubectl config use-context k8s
```

레이블이 app=webui 인 파드들을 레이블과 함께 확인합니다.
```sh
kubectl get pod --selector app=webui --show-labels
```

찾은 파드의 상세정보에서 사용하고 있는 포트를 확인합니다.
```sh
kubectl describe pod nginx-29488c9578-88jg9
```

서비스 생성용 yaml 파일 작성합니다.
```sh
vi my-service.yaml
```
```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-service
spec:
  type: NodePort
  selector:
    app: webui
  ports:
    - port: 80
      targetPort: 80
      nodePort: 32767
```

서비스 생성용 yaml 파일을 적용합니다.
```sh
kubectl apply -f my-service.yaml
```

생성된 서비스를 확인합니다.
```sh
kubectl get svc
```

NodePort로 접근되는지 확인합니다.
```sh
curl k8s-worker1:32767
```

## 16. ConfigMap 운영

CKAD에서 더 자주 나오는 문제입니다.

### 문제

```
Set configuration context $ kubectl config use-context k8s

Expose Configuration settings
Task:
- All operations in this question should be performed in the ckad namespace.
- Create a ConfigMap called web-config that contains the following two entries:
  - connection_string=localhost:80
  - external_url=cncf.io
- Run a pod called web-pod with a single container running the nginx:1.19.8-alpine image, and expose these configuration settings as environment variables inside the container.
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `pod configmap`

https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/#create-configmaps-from-literal-values

https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/#define-a-container-environment-variable-with-data-from-a-single-configmap

### 답안

컨텍스트를 적용합니다.
```sh
kubectl config use-context k8s
```

네임스페이스를 생성합니다.
```sh
kubectl create namespace ckad
```

ckad 네임스페이스에 configmap을 생성합니다.
```sh
kubectl create configmap web-config \
  --from-literal=connection_string=localhost:80 \
  --from-literal=external_url=cncf.io \
  -n ckad
```

생성한 configMap을 확인합니다.
```sh
kubectl desribe configmap -n ckad
```

파드 정의를 위한 yaml 파일을 만듭니다.
```sh
kubectl run web-pod --image=nginx:1.19.8-alpine -n ckad --dry-run=client -o yaml > web-pod.yaml
```

파드 정의를 위한 yaml 파일에 configmap을 위한 env를 추가합니다.
```sh 
vi web-pod.yaml
```

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: web-pod
  namespace: ckad
spec:
  containers:
  - image: nginx:1.19.8-alpine
    name: web-pod
    envFrom:
    - configMapRef:
        name: web-config # configMap 설정
    ports:
    - containerPort: 80
```

작성한 yaml 파일을 반영합니다.
```sh
kubectl apply -f web-pod.yaml
```

생성된 파드에 환경변수(env)가 제대로 설정됐는지 확인합니다.
```sh
kubectl exec -it ckad web-pod -- env
```

## 17. Secret 운영

CKAD에서 더 자주 나오는 문제입니다.

### 문제

Secret를 생성한 후에 Pod에 전달합니다.

```
Cluster: kubectl config use-context k8s
Create a Kubernetes secret and expose using a file in the pod.
- Create a Kubernetes Secret as follows:
  - name: super-secret
  - data:
    password=secretpass
Create a Pod named pod-secrets-via-file, using the redis image, which mounts a secret named super-secret at /secrets.
Create a second Pod named pod-secrets-via-env, using the redis image, which exports password as PASSWORD.
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `secret` (검색 결과 중 Distribute Credentials Securely Using Secrets 페이지)

시크릿은 암호, 토큰 또는 키와 같은 소량의 중요한 데이터를 포함하는 오브젝트(key-value구조)입니다. 

시크릿에 포함하는 밸류는 base64로 인코딩된 문자열이어야 합니다. 밸류가 binary이면, ascii text로 변환하고 그 값을 base64로 인코딩한 문자열을 저장해야 합니다.

https://kubernetes.io/docs/tasks/inject-data-application/distribute-credentials-secure/#define-container-environment-variables-using-secret-data

https://kubernetes.io/docs/tasks/inject-data-application/distribute-credentials-secure/#create-a-pod-that-has-access-to-the-secret-data-through-a-volume

secret 유형은 docker-registry, generic, tls가 있으며, 기본 key-value구조는 generic 입니다.

```sh
# base64 인코딩하기
echo "a" | base64
```

```sh
# base64 디코딩하기
echo "YQo=" | base64 -d
```

### 답안

컨텍스트를 적용합니다.
```sh
kubectl config use-context k8s
```

시크릿을 생성합니다.
```sh
kubectl create secret generic super-secret --from-literal='password=secretpass' 
```

시크릿 정보를 확인합니다.
```sh
kubectl get secrets super-secret -o yaml
```

파드 정의를 위한 yaml 파일을 만듭니다.
```sh
kubectl run pod-secrets-via-file --image=redis --dry-run=client -o yaml > pod-secrets-via-file.yaml
```

파드 정의를 위한 yaml 파일에 secret을 volumeMounts 방식으로 추가합니다.
```sh 
vi pod-secrets-via-file.yaml
```

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: pod-secrets-via-file
spec:
  containers:
  - image: redis
    name: pod-secrets-via-file
    volumeMounts:
      - name: secret-volume
        mountPath: /secrets
        readOnly: true
  volumes:
    - name: secret-volume
      secret:
        secretName: super-secret
```

작성한 yaml 파일을 사용해서 파드를 반영합니다.
```sh
kubectl apply -f pod-secrets-via-file.yaml
```

생성된 파드에 volumeMounts가 됐는지 확인합니다.
```sh
kubectl exec -it pod-secrets-via-file -- ls /secrets
kubectl exec -it pod-secrets-via-file -- cat /secrets/password
```

secret을 env 방식으로 추가하는 두 번째 파드를 위한 yaml 파일을 작성합니다.
```sh 
vi pod-secrets-via-env.yaml
```

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: pod-secrets-via-env
spec:
  containers:
  - image: redis
    name: pod-secrets-via-env
    env:
    - name: PASSWORD
      valueFrom:
        secretKeyRef:
          name: super-secret
          key: password
```

작성한 yaml 파일을 사용해서 파드를 반영합니다.
```sh
kubectl apply -f pod-secrets-via-env.yaml
```

생성된 파드에서 env를 됐는지 확인합니다.
```sh
kubectl exec -it pod-secrets-via-env -- env
```

## 18. Ingress 구성

인그레스는 클러스터 외부에서 클러스터 내부 서비스로 HTTP와 HTTPS 경로를 노출합니다. 트래픽 라우팅은 인그레스 리소스에 정의된 규칙에 의해 제어됩니다.

### 문제 

```
Cluster: kubectl config use-context k8s
- Application Service 운영
  - ingress-nginx namespace에 nginx 이미지를 app=nginx 레이블을 가지고 실행하는 nginx pod를 구성하시요.
  - 앞서 생성한 nginx Pod를 서비스하는 nginx service를 생성하시오.
  - 현재 appjs-service Service는 이미 동작 중입니다. 별도 구성이 필요 없습니다.
- Ingress 구성
  - app-ingress.yaml 파일을 생성하여 다음 조건의 ingress 서비스를 구성하시오.
    - ingress name: app-ingress
    - 30080 포트  NodePort 서비스는 사전에 구성되어 있습니다.
    - NODE_PORT:30080/ 접속했을 때 nginx 서비스로 연결
    - NODE_PORT:30080/app 접속했을 떄 appjs-service 서비스로 연결
    - ingress 구성에 다음의 annotation을 포함하시오.
      annotations:
        kubernetes.io/ingress.class: nginx
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `ingress` 

https://kubernetes.io/docs/concepts/services-networking/ingress/#the-ingress-resource

### 답안

컨텍스트를 적용합니다.
```sh
kubectl config use-context k8s
```

nginx 파드를 구성하고 확인합니다.
```sh
kubectl run nginx --image=nginx --labels=app=nginx -n ingress-nginx
kubectl get pod -n ingress-nginx
```

nginx 파드에 대한 서비스를 expose하고 확인합니다.
```sh
kubectl expose -n ingress-nginx pod nginx --port=80 --target-port=80
kubectl desribe svc -n ingress-nginx
```

ingress에 연결할 서비스들의 포트를 확인합니다.
```sh
kubectl get svc -n ingress-nginx
NAME           TYPE       CLUSTER-IP     EXTERNAL-IP  PORT(S)  AGE
appjs-service  ClusterIP  10.101.79.81   <none>       80/TCP   110d
nginx          ClusterIP  10.106.127.46  <none>       80/TCP   3m12s
```

ingress 정의 파일을 작성합니다.
```sh
vi app-ingress.yaml
```
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  namespace: ingress-nginx # 네임스페이스 지정
  name: app-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    kubernetes.io/ingress.class: nginx
spec:
  rules:
  - http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: nginx
            port:
              number: 80 # 서비스에서 확인한 포트
      - path: /app
        pathType: Prefix
        backend:
          service:
            name: appjs-service
            port:
              number: 80 # 서비스에서 확인한 포트
```

파일을 이용해서 ingress를 생성하고 확인합니다.
```sh
kubectl apply -f app-ingress.yaml
kubectl describe ingress -n ingress-nginx app-ingress
```

URL에 따라 잘 동작하는지 확인합니다.
```sh
curl k8s-workder1:30080/
curl k8s-workder1:30080/app
```

### 참고 - 기출 문제

```
Create a new nginx Ingress resource as follows:
- name: ping
- namespace: ing-internal
- exposing service hi on path /hi using service port 5678

The availability of service hi can be checked using the following command which should return hi:
curl -KL <INTERNAL_IP>/hi
```

```sh
kubectl create ingress ping -n ing-internal --rule="/hi=hi:5678"
```

## 19. Persistent Volume 생성

### 문제

```
kubectl config use-context k8s
- Create a persistent volume with name app-config, of capacity 1Gi and access mode ReadWriteMany.
- storageClass: az-c
- The type of volume is hostPath and its location is /srv/app-config.
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `Persistent Volumes` 

https://kubernetes.io/docs/concepts/storage/persistent-volumes/#persistent-volumes

https://kubernetes.io/docs/tasks/configure-pod-container/configure-persistent-volume-storage/#create-a-persistentvolumeclaim

### 답안

컨텍스트를 적용합니다.
```sh
kubectl config use-context k8s
```

PV 생성을 위한 yaml 파일을 작성합니다.
```sh
vi app-pv.yaml
```
```yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: app-config
  labels:
    type: local
spec:
  storageClassName: az-c
  capacity:
    storage: 1Gi
  accessModes:
    - ReadWriteMany
  hostPath:
    path: "/srv/app-config"
```

작성한 파일을 이용해서 pv를 생성하고 확인합니다.
```sh
kubectl apply -f app-pv.yaml
kubectl describe pv -n app-config
```

## 20. Persistent Volume Claim을 사용하는 Pod

### 문제

```
Cluster: kubectl config use-context k8s
- Create a new PersistentVolumeClaim
  - name: app-volume
  - StorageClass: app-hostpath-sc
  - Capacity: 10Mi
- Create a new Pod which mounts the PersistentVolumeClaim as a volume
  - nane: web-server-pod
  - image: nginx
  - mount path: /usr/share/nginx/html
- Configure the new Pod to have ReadWriteMany access on the volume
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `persistent volume claim` 

https://kubernetes.io/docs/tasks/configure-pod-container/configure-persistent-volume-storage/#create-a-persistentvolumeclaim

### 답안

컨텍스트를 적용합니다.
```sh
kubectl config use-context k8s
```

PVC 생성을 위한 yaml 파일을 작성합니다.
```sh
vi pvc.yaml
```
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: app-volume
spec:
  storageClassName: app-hostpath-sc
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: 10Mi
```

작성한 파일을 이용해서 pvc를 생성합니다.
```sh
kubectl apply -f pvc.yaml
```

생성된 pvc를 확인하면 PV를 지정하지 않았음에도 기존에 존재하던 PV 중 적합한 PV가 사용된 것이 확인됩니다.
```sh
kubectl get pvc
```

Kubernetes 문서를 참고해서 파드 생성욜 yaml 파일을 작성합니다.
```sh
vi web-server-pod.yaml
```
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: web-server-pod
spec:
  volumes:
    - name: app-volume-storage
      persistentVolumeClaim:
        claimName: app-volume
  containers:
    - name: web-server-pod
      image: nginx
      ports:
        - containerPort: 80
          name: "http-server"
      volumeMounts:
        - mountPath: "/usr/share/nginx/html"
          name: app-volume-storage
```

작성한 파일을 이용해서 파드를 생성합니다.
```sh
kubectl apply -f web-server-pod.yaml
```

파드 상세정보에서 volumeMount 정보를 확인합니다.
```sh
kubectl describe pod web-server-pod
```

## 21. Check Resource Information

### 문제

다양한 리소스(파드,서비스,PV 등)에 대한 정보를 조회하고 파일로 남기기

```
Set configuration context: kubectl config use-context k8s
- List all 'PV's sorted by name saving the full kubectl output to /var/CKA2023/my_volumes.
- Use kubectl's own functionally for sorting the output, and do not manipluate it any further.
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `cheat sheet` 키워드 검색된 페이지에서 sort로 검색하면 원하는 명령을 찾기 쉬움

https://kubernetes.io/docs/reference/kubectl/cheatsheet/#viewing-and-finding-resources

### 답안

컨텍스트를 적용합니다.
```sh
kubectl config use-context k8s
```

```sh
kubectl get pv -A --sort-by=.metadata.name > /var/CKA2023/my_volumes
```

## 22. Kubernetes Upgrade

### 문제

Master 노드만 업그레이드하기

```
upgrade system: k8s-master
Given an existing Kubernetes cluster running version 1.26.0.
upgrade all of the Kubernetes control plane and node components on the master node only to version 1.27.1.
Be sure to drain the master node before upgrading it and uncordon it after upgrade.
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `upgrade` 키워드 검색된 페이지에서 버전에 맞는 페이지에 나오는 내용을 그대로 따라하기

https://kubernetes.io/docs/tasks/administer-cluster/kubeadm/kubeadm-upgrade

### 참고: 쿠버네티스 설치

1. 1단계
모든 노드(control plane, worker1, worker2, ...)에 컨테이너 엔진 containerd를 설치합니다.
2. 2단계 - 시험상 update해야 하는 대상
모든 노드(control plane, worker1, worker2, ...)에 설치도구인 kubeadm를 설치하고, kubelet도 설치합니다. kubelet은 `systemctl start kubelet`으로 서비스를 시작합니다.
3. 3단계 - 시험상 update해야 하는 대상
control plane 노드에서 kubeadm을 이용해서 apiserver, controller, scheduler, etcd 등을 실행합니다.
4. 4단계 - 시험상 update해야 하는 대상
worker 노드에서 kubeadm join master 합니다.

시험환경의 kubernetes 설치 도구는 모두 kubeadm 입니다.

### 답안

control plane 노드(master노드)로 이동합니다.
```sh
console$ ssh k8s-master
```

root 유저로 전환합니다.
```sh
user@k8s-master$ sudo -i
```

업그레이드할 버전 정보를 가져오기 위해 명령을 실행합니다.

```sh
root@k8s-master# apt update
root@k8s-master# apt-cache madison kubeadm
```

kubeadm 업그레이드를 합니다. (버전 지정에 주의하세요)

```sh
root@k8s-master# apt-mark unhold kubeadm && \
 apt-get update && apt-get install -y kubeadm=1.27.x-00 && \
 apt-mark hold kubeadm
```

설치된 kubeadm 버전을 확인합니다.
```sh
root@k8s-master# kubeadm version
```

control plane 노드에 있는 컴포넌트들(apiserver, controller, scheduler, etcd 등)을 어떤 버전까지 업그레이드 할 수 있는지 확인합니다.
```sh
root@k8s-master# kubeadm upgrade plan
```

control plane 노드에 있는 컴포넌트들을 업그레이드 합니다.
```sh
root@k8s-master# sudo kubeadm upgrade apply v1.27.x
```

잘 진행됐다면 아래와 같은 메세지를 확인합니다.
```sh
[upgrade/successful] SUCCESS! Your cluster was upgraded to "v1.27.x". Enjoy!
```

kubelet을 업그레이드하기 위해 control plane 노드에서 파드들을 모두 비우고 스케줄을 못 받게 합니다. 이 작업은 kubectl 명령을 사용해야 하므로 control plane 노드 외부로 나간 후에 작업합니다.
```sh
root@k8s-master# exit
user@k8s-master# exit
console$ kubectl drain k8s-master --ignore-daemonsets
```

모든 노드들의 상태를 확인해서 control plane 노드가 Scheduling을 받지 않게 된 것을 확인합니다.
```sh
console$ kubectl get nodes
```

다시 control plane 노드에 root 유저로 접속합니다.
```sh
console$ ssh k8s-master
user@k8s-master$ sudo -i
```

kubelet과 kubectl을 업그레이드 합니다.
```sh
root@k8s-master# apt-mark unhold kubelet kubectl && \
apt-get update && apt-get install -y kubelet=1.27.x-00 kubectl=1.27.x-00 && \
apt-mark hold kubelet kubectl
```

kubelet을 재시작합니다.
```sh
root@k8s-master# sudo systemctl daemon-reload
root@k8s-master# sudo systemctl restart kubelet
```

control plane 노드가 스케줄링 받을 수 있게 합니다. 이 작업은 kubectl 명령을 사용해야 하므로 control plane 노드 외부로 나간 후에 작업합니다.
```sh
root@k8s-master# exit
user@k8s-master# exit
console$ kubectl uncordon k8s-master
```

## 23. Troubleshooting (1)

### 문제

Not Ready 상태의 노드를 활성화하시오.

```
A Kubernetes worker node, named hk8s-w2 is in state NotReady.
Investigate why this is the case, and perform any appropriate steps to bring the node to a Ready state, ensuring that any changes are made permanent.
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `upgrade` 키워드 검색된 페이지에서 버전에 맞는 페이지에 나오는 내용을 그대로 따라하기

https://kubernetes.io/docs/tasks/administer-cluster/kubeadm/kubeadm-upgrade

### 참고: worker 노드가 Ready 상태가 되기 위해 필수적인 요소들

- containerd: 컨테이너를 동작시켜주는 컨테이너 엔진
- kubelet: 클러스터를 운영해주는 역할
- kube-proxy: 쿠버네티스의 네트웍을 구성 = 클라이언트의 커넥션을 받아주는 네트웍 포트 listen, worker node안에 iptables 등을 이용해서 서비스 구성 등
- CNI(Contain Network Interface): 컨테이너간 통신과 외부 네트워크와 연결을 관리하는 인터페이스 제공 (flannel, calico 등)

### 답안

컨텍스트를 적용합니다.
```sh
kubectl config use-context hk8s
```

노드들의 상태를 확인하여, NotReady 상태인 노드를 확인합니다.
```sh
console$ kubectl get nodes
```

NotReady 상태인 노드에 접속하고 root 유저로 전환합니다.
```sh
console$ ssh hk8s-w2
user@hk8s-w2$ sudo -i
root@hk8s-w2#
```

containerd(컨테이너 엔진) 상태가 active (running) 상태인지 확인합니다.
```sh
root@hk8s-w2# systemctl status containerd
```

kubelet 상태가 active (running) 상태인지 확인합니다.
```sh
root@hk8s-w2# systemctl status kubelet
```

kubelet 상태가 active (running) 상태가 아니므로 지금 즉시 실행시키고 다음 부팅시에도 실행되도록 합니다.
```sh
root@hk8s-w2# systemctl enable --now kubelet
```

다시 kubelet 상태가 active (running) 상태인지 확인합니다.
```sh
root@hk8s-w2# systemctl status kubelet
```

kube-proxy와 CNI 관련 파드들의 상태가 Running인지 확인합니다. (이 작업은 콘솔로 나가서 pod 상태로 확인해야 합니다.) <br>
CNI 관련 Pod들은 calico- 혹은 flannel-인 경우가 대부분입니다.
```sh
root@hk8s-w2# exit
user@hk8s-w2# exit
console$ kubectl get pod -n kube-system -o wide
```

노드들의 상태를 확인하여, 모든 노드가 Ready 상태가 되었는지 확인합니다.
```sh
console$ kubectl get nodes
```

## 24. Troubleshooting (2)

### 문제

Not Ready 상태의 노드를 활성화하시오.

```
A Kubernetes worker node, named hk8s-w2 is in state NotReady.
Investigate why this is the case, and perform any appropriate steps to bring the node to a Ready state, ensuring that any changes are made permanent.
```

### 답안

컨텍스트를 적용합니다.
```sh
kubectl config use-context hk8s
```

노드들의 상태를 확인하여, NotReady 상태인 노드를 확인합니다.
```sh
console$ kubectl get nodes
```

NotReady 상태인 노드에 접속하고 root 유저로 전환합니다.
```sh
console$ ssh hk8s-w2
user@hk8s-w2$ sudo -i
root@hk8s-w2#
```

containerd(컨테이너 엔진) 상태가 active (running) 상태인지 확인합니다.
```sh
root@hk8s-w2# systemctl status containerd
```

containerd(컨테이너 엔진) 상태가 상태가 아니므로 지금 즉시 실행시키고 다음 부팅시에도 실행되도록 합니다.
```sh
root@hk8s-w2# systemctl enable --now containerd
```

다시 containerd(컨테이너 엔진) 상태가 active (running) 상태인지 확인합니다.
```sh
root@hk8s-w2# systemctl status containerd
```

kubelet 상태가 active (running) 상태인지 확인합니다.
```sh
root@hk8s-w2# systemctl status kubelet
```

kube-proxy와 CNI 관련 파드들의 상태가 Running인지 확인합니다. (이 작업은 콘솔로 나가서 pod 상태로 확인해야 합니다.) <br>
CNI 관련 Pod들은 calico- 혹은 flannel-인 경우가 대부분입니다.
```sh
root@hk8s-w2# exit
user@hk8s-w2# exit
console$ kubectl get pod -n kube-system -o wide
```

노드들의 상태를 확인하여, 모든 노드가 Ready 상태가 되었는지 확인합니다.
```sh
console$ kubectl get nodes
```

## 25. User Role Binding

### 문제

User API 인증(authentication) 구성하기
```
Cluster: kubectl config use-context k8s

Task:
Create the kubeconfig named ckauser:
- username: ckauser
- certificate location: /data/cka/ckauser.crt, /data/cka/ckauser.key
- context-name: ckauser
- ckauser cluster must be operated with the privileges of the ckauser account.

Create a role named pod-role that can create, delete, watch, list, get pods.
Create the following rolebinding.
- name: pod-rolebinding
- role: pod-role
- user: ckauser
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `csr`

https://kubernetes.io/docs/reference/access-authn-authz/certificate-signing-requests/#create-role-and-rolebinding

### 참고: API 인증

1. User
user가 kubectl 같은 명령을 통해서 apiserver에 요청을 하면, apiserver는 인증(authentication)을 한 후에 권한(authorization)을 검사하고 명령을 실행합니다.
2. Service Account
모든 컨테이너는 Service Account라는 걸 가지고 동작합니다. apiserver는 Service Account가 가진 권한에 따라서 컨테이너의 요청을 실행하기도 거부하기도 합니다.

### 답안

role을 생성합니다. 명령문은 검색된 k8s document 가장 하단을 참고합니다.
```sh
kubectl create role pod-role --verb=create,delete,watch,list,get --resource=pods
```

생성한 role을 확인합니다.
```sh
kubectl describe role pod-role
```

rolebinding을 생성합니다.
```sh
kubectl create rolebinding pod-rolebinding --role=pod-role --user=ckauser
```

생성한 rolebinding을 확인합니다.
```sh
kubectl describe rolebinding pod-rolebinding
```

kubeconfig에 user와 credentials를 추가합니다.
```sh
kubectl config set-credentials ckauser --client-key=/data/cka/ckauser.key --client-certificate=/data/cka/ckauser.crt --embed-certs=true
```

context를 추가합니다.
```sh
kubectl config set-context ckauser --cluster=kubernetes --user=ckauser
```

생성한 컨텍스트를 적용하고, 확인합니다.
```sh
kubectl config use-context ckauser
kubectl get pods # 실행 가능한 명령
kubectl get svc # 권한이 없으므로 실행 불가능한 명령
```

## 26. User Cluster Role Binding

### 문제

```
Cluster: kubectl config use-context k8s

Task:
- Create a new ClusterRole named app-clusterrole, which only allows to get,watch,list the following resource types: Deployment, Service
- Bind the new ClusterRole app-clusterrole to the new user ckauser.
- User ckauser and ckauser clusters are already configured.
- To check the results, run the following command:
  kubectl config use-context ckauser
```

### Role과 Cluster Role의 차이

- Role: 특정 namespace에서만 적용되는 권한을 가질 수 있다.
- Cluster Role: namespace에 관계없이 클러스터 전체에 적용되는 권한을 가질 수 있다.

### K8S 도큐먼트 사이트 참조

검색 키워드: `clusterrole`

https://kubernetes.io/docs/reference/access-authn-authz/rbac/#kubectl-create-clusterrole

### 답안

컨텍스트를 적용합니다.
```sh
kubectl config use-context k8s
```

clusterrole을 생성합니다.
```sh
kubectl create clusterrole app-clusterrole --verb=get,list,watch --resource=deployment,service
```

셍성한 clusterrole을 확인합니다.
```sh
kubectl describe clusterrole app-clusterrole
```

clusterrolebinding을 생성합니다.
```sh
kubectl create clusterrolebinding app-clusterrole-binding --clusterrole=app-clusterrole --user=ckauser
```

생성한 clusterrolebinding을 확인합니다.
```sh
kubectl describe clusterrolebinding app-clusterrole-binding
```

생성한 컨텍스트를 적용하고, 확인합니다.
```sh
kubectl config use-context ckauser
kubectl get svc -A # 실행 가능한 명령
kubectl get pods -A # 권한이 없으므로 실행 불가능한 명령
```

원래 컨텍스트를 적용합니다.
```sh
kubectl config use-context kubernetes-admin@kubernetes
```

## 27. ServcieAccount Role Binding

### 문제

```
Cluster: kubectl config use-context k8s
Create the ServiceAccount named pod-access in a new namespace called apps.
Create a Role with the name pod-role and the RoleBinding named pod-rolebinding.
Map the ServiceAccount from the previous step to the API resources Pods with the operations watch,list,get.
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `kubectl reference`

https://kubernetes.io/docs/reference/generated/kubectl/kubectl-commands

검색 키워드: `csr`

https://kubernetes.io/docs/reference/access-authn-authz/certificate-signing-requests/#create-role-and-rolebinding

### 답안

컨텍스트를 적용합니다.
```sh
kubectl config use-context k8s
```

네임스페이스를 생성합니다.
```sh
kubectl create ns apps
```

ServiceAccount를 생성합니다. (명령문은 kubectl reference 페이지를 참고합니다.)
```sh
kubectl create serviceaccount pod-access --namespace apps
```

생성한 ServiceAccount를 확인합니다.
```sh
kubectl sa pod-access --namespace apps
```

role을 생성합니다. 명령문은 검색된 k8s document 가장 하단을 참고합니다.
```sh
kubectl create role pod-role --verb=watch,list,get --resource=pods --namespace apps
```

생성한 role을 확인합니다.
```sh
kubectl get role -n apps
```

rolebinding을 생성합니다. ServiceAccount와 연결할 때는 namespace정보가 누락되지 않도록 주의합니다.
```sh
kubectl create rolebinding pod-rolebinding --role=pod-role --serviceaccount=apps:pod-access --namespace apps
```

생성한 rolebinding을 확인합니다.
```sh
kubectl describe rolebinding pod-rolebinding --namespace apps
```

## 28. ServiceAccount Cluster Role Binding

### 문제

```
- Create a new ClusterRole named deployment-clusterrole.
  which only allows to create the follwing resource types: Deployment StatefulSet DaemonSet
- Create a new ServiceAccount named cicd-token in the existing namespace apps.
- Bind the new ClusterRole deployment-clusterrole to the new ServiceAccount cicd-token, limited to the namespace apps.
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `kubectl reference`

https://kubernetes.io/docs/reference/generated/kubectl/kubectl-commands

검색 키워드: `csr`

https://kubernetes.io/docs/reference/access-authn-authz/certificate-signing-requests/#create-role-and-rolebinding

### 답안

컨텍스트를 적용합니다.
```sh
kubectl config use-context k8s
```

네임스페이스를 생성합니다.
```sh
kubectl create ns apps
```

SerivceAccount를 생성합니다.
```sh
kubectl create serviceaccount cicd-token -n apps
```

생성한 ServiceAccount를 확인합니다.
```sh
kubectl sa pod-access --namespace apps
```

clusterrole을 생성합니다. (문제에 namespace 지정이 없었으므로 생략)
```sh
kubectl create clusterrole deployment-clusterrole --verb=create --resource=deployment,statefulset,daemonset
```

생성한 clusterrole을 확인합니다.
```sh
kubectl get clusterrole -A
```

clusterrolebinding을 생성합니다. ServiceAccount와 연결할 때는 namespace정보가 누락되지 않도록 주의합니다.
```sh
kubectl create clusterrolebinding deployment-clusterrole-binding --clusterrole=deployment-clusterrole --serviceaccount=apps:cicd-token
```

생성한 clusterrolebinding을 확인합니다.
```sh
kubectl describe clusterrolebinding deployment-clusterrole-binding
```

## 29. Kube-DNS

### 문제

```
작업 클러스터: k8s

- Create a nginx pod called nginx-resolver using image nginx, expose it internally with a service called nginx-resolver-service.
- Test that you are able to look up the service and pod names from within the cluster. 
  Use this image: busybox:1.28 for dns lookup.
  - Record results in /tmp/nginx.svc and /tmp/nginx.pod
  - pod: nginx-resolver created
  - Service DNS Resolution recorded correctly
  - Pod DNS resolution recorded correctly
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `dns`

https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/

쿠버네티스에서 동작하는 모든 파드는 기본적으로 /etc.resolv.conf 파일에 DNS서버의 정보(nameserver 정보, search 옵션 등)를 갖습니다. 즉, 그 정보가 coreDNS 정보입니다.

nslookup 명령은 nameserver에게 

#### DNS Records

1. 서비스용 DNS 형식: <서비스이름>.<네임스페이스>.svc.<클러스터도메인>
2. 파드용 DNS 형식: <파드의IP>.<네임스페이스>.pod.<클러스터도메인>

클러스터도메인의 기본 값 = clsuter.local

### 답안

컨텍스트를 적용합니다.
```sh
kubectl config use-context k8s
```

파드를 생성합니다.
```sh
kubectl run nginx-resolver --image=nginx
```

생성한 파드 상세정보에서 서비스할 포트와 IP를 확인합니다.
```sh
kubectl describe pod nginx-resolver
```

확인한 포트를 바탕으로 생성한 파드를 위한 서비스를 생성합니다.
```sh
kubectl expose pod nginx-resolver --name nginx-resolver-service --port=80 --target-port=80
```

busybox:1.28 이미지를 이용해서 테스트용 파드를 생성하고, 테스트용 파드에서 nslookup 명령을 사용해서 서비스의 DNS lookup을 한 결과를 파일에 담습니다.
```sh
kubectl run test-pod --image=busybox:1.28 --rm -it --restart=Never -- nslookup nginx-resolver-service.default.svc.cluster.local > /tmp/nginx.svc
```

busybox:1.28 이미지를 이용해서 테스트용 파드를 생성하고, 테스트용 파드에서 nslookup 명령을 사용해서 파드의 DNS lookup을 한 결과를 파일에 담습니다. 이 때, nginx-resolver 파드의 IP는 앞에서 파드를 생성한 후에 확인한 IP를 사용합니다.
```sh
kubectl run test-pod --image=busybox:1.28 --rm -it --restart=Never -- nslookup 10-244-1-163.default.pod.cluster.local > /tmp/nginx.pod
```

작성된 파일 내용을 확인합니다.
```sh
cat /tmp/nginx.svc
cat /tmp/nginx.pod
```

## 30. Network Policy

### 문제

Network Policy with Namespace

```
작업 클러스터: hk8s
- Create a new NetworkPolicy named allow-port-from-namespace in the existing namespace devops.
- Ensure that the new NetworkPolidy allow Pods in namespace migops to connect to port 80 of Pods in namespace devops.
```

### K8S 도큐먼트 사이트 참조

검색 키워드: `network policy`

https://kubernetes.io/docs/concepts/services-networking/network-policies/


- AWS의 Security Group에 하는 ingress, egress 설정과 유사합니다.
- ingress와 egress는 ip 범위, namespace, pod를 대상으로 적용할 수 있습니다.

### 답안

컨텍스트를 적용합니다.
```sh
kubectl config use-context hk8s
```

devops와 migops 네임스페이스가 있는지 확인합니다.
```sh
kubectl get ns
```

migops 네임스페이스가 team=migops 레이블을 가지고 있는 것을 확인합니다.
```sh
kubectl get ns migops --show-labels
```

devops 네임스페이스에 있는 파드들이 app=web 레이블을 가지고 있는 것을 확인합니다.
```sh
kubectl get pods -n devops --show-labels
```

K8S 도큐먼트를 참고하여 NetworkPolicy 용도의 yaml 파일을 작성합니다.
```sh
vi networkpolicy.yaml
```
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-port-from-namespace
  namespace: devops # devops 네임스페이스에
spec:
  podSelector:
    matchLabels:
      app: web # 레이블이 app=web인 파드들을 대상으로
  policyTypes:
    - Ingress
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              team: migops # 네임스페이스 레이블이 team=migops인 경우
      ports:
        - protocol: TCP
          port: 80 # 80포트로 접근을 허용함
```

작성한 파일을 적용합니다.
```sh
kubectl apply -f networkpolicy.yaml
```

생성한 NetworkPolicy를 확인합니다.
```sh
kubectl get networkpolicy -n devops
```
 