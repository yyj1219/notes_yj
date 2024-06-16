# 1. Kubernetes 업그레이드

## controlplane 노드 업그레이드

```sh
console$ kubectl drain controlplane --ignore-daemonsets

root@controlplane# apt update

root@controlplane# apt-cache madison kubeadm

root@controlplane# apt-mark unhold kubeadm && \
 apt-get update && apt-get install -y kubeadm=1.27.x-00 && \
 apt-mark hold kubeadm

root@controlplane# kubeadm version

# controlplane 노드에서만 실행
root@controlplane# kubeadm upgrade plan v1.27.x

# controlplane 노드에서만 실행
root@controlplane# sudo kubeadm upgrade apply v1.27.x

root@controlplane# apt-mark unhold kubelet kubectl && \
  apt-get update && apt-get install -y kubelet=1.27.x-00 kubectl=1.27.x-00 && \
  apt-mark hold kubelet kubectl

root@controlplane# sudo systemctl daemon-reload
root@controlplane# sudo systemctl restart kubelet

console$ kubectl uncordon $(hostname)
```

## controlplane 노드에도 스케줄링이 가능하게 하기 

controlplane 노드에서 taint 확인하기
```sh
root@controlplane# kubectl describe node controlplane | grep -i taint
```

"kubectl taint" 명령을 사용해서 controlplane 노드의 NoSchedule 설정빼기
```sh
root@controlplane# kubectl taint node controlplane \ 
  node-role.kubernetes.io/control-plane:NoSchedule-
```

## worker 노드 업그레이드

```sh
console$ kubectl drain node01 --ignore-daemonsets

root@node01# apt update

root@node01# apt-cache madison kubeadm

root@node01# apt-mark unhold kubeadm && \
 apt-get update && apt-get install -y kubeadm=1.27.x-00 && \
 apt-mark hold kubeadm

root@node01# kubeadm version

# worker 노드에서만 실행
root@node01# kubeadm upgrade node

root@node01# apt-mark unhold kubelet kubectl && \
  apt-get update && apt-get install -y kubelet=1.27.x-00 kubectl=1.27.x-00 && \
  apt-mark hold kubelet kubectl

root@node01r# sudo systemctl daemon-reload
root@node01# sudo systemctl restart kubelet

console$ kubectl uncordon node01
```

# 2. kubectl에서 custom-columns 사용 (jsonPath)

```sh
kubectl get deployments.apps -n admin2406 -o \
  custom-columns=DEPLOYMENT:.metadata.name,CONTAINER_IMAGE:.spec.template.spec.containers[].image,READY_REPLICAS:.status.readyReplicas,NAMESPACE:.metadata.namespace --sort-by=.metadata.name > /opt/admin2406_data
```

# 3. kubeconfig 오류 수정

```sh
cat /root/CKA/admin.kubeconfig
```

클러스터 정보를 확인하여, admin.kubeconfig 파일에서 일치하지 않는 Kubernetes control plane 주소를 수정합니다.
```sh
# kubectl cluster-info 혹은 kubectl config view
Kubernetes control plane is running at https://controlplane:6443
CoreDNS is running at https://controlplane:6443/api/v1/namespaces/kube-system/services/kube-dns:dns/proxy
```

# 4. deployment를 rolling update로 이미지 변경

```sh
kubectl create deployment nginx-deploy --image=nginx:1.16 --replicas=1

kubectl set image deployment/nginx-deploy nginx=nginx:1.17 --record
```

롤아웃 상태 확인
```sh
kubectl rollout status deployment nginx-deploy
```

롤아웃 이력 확인
```sh
kubectl rollout history deployment nginx-deploy
```

롤백하기
```sh
kubectl rollout undo deployment nginx-deploy
```

# 5. PV, PVC 오류 수정

PVC 문제로 인해 파드 스케줄이 실패한 상태임을 확인하기
```sh
# kubectl describe pod -n alpha alpha-mysql-5b7b8988c4-hq7bk
... 
Events:
  Type     Reason            Age                    From               Message
  ----     ------            ----                   ----               -------
  Warning  FailedScheduling  4m30s (x2 over 4m32s)  default-scheduler  0/2 nodes are available: persistentvolumeclaim "mysql-alpha-pvc" not found. preemption: 0/2 nodes are available: 2 No preemption victims found for incoming pod..
```

PV 정보 확인하기 - StorageClass, Access Modes, Capacity 중요
```sh
# kubectl describe pv alpha-pv 
Name:            alpha-pv
Labels:          <none>
Annotations:     <none>
Finalizers:      [kubernetes.io/pv-protection]
StorageClass:    slow
Status:          Available
Claim:           
Reclaim Policy:  Retain
Access Modes:    RWO
VolumeMode:      Filesystem
Capacity:        1Gi
Node Affinity:   <none>
Message:         
Source:
    Type:          HostPath (bare host directory volume)
    Path:          /opt/pv-1
    HostPathType:  
Events:            <none>
```

PVC 정보 확인하기
```sh
# kubectl -n alpha describe pvc alpha-claim 
Name:          alpha-claim
Namespace:     alpha
StorageClass:  slow-storage
Status:        Pending
Volume:        
Labels:        <none>
Annotations:   <none>
Finalizers:    [kubernetes.io/pvc-protection]
Capacity:      
Access Modes:  
VolumeMode:    Filesystem
Used By:       <none>
Events:
  Type     Reason              Age                     From                         Message
  ----     ------              ----                    ----                         -------
  Warning  ProvisioningFailed  2m43s (x26 over 8m44s)  persistentvolume-controller  storageclass.storage.k8s.io "slow-storage" not found
```

PV와 다르게 설정되어 있던 StorageClass, Access Modes, Capacity를 수정하기
```sh
# kubectl get -n alpha pvc alpha-claim -o yaml > pvc.yaml
# vi pvc.yaml
```
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: alpha-claim
  namespace: alpha
spec:
  accessModes:
  - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
  storageClassName: slow
  volumeMode: Filesystem
```

Deployment의 PVC 이름을 수정하기
```sh
# kubectl edit -n alpha deployments.apps alpha-mysql
```
```yaml
...
      volumes:
      - name: mysql-data
        persistentVolumeClaim:
          claimName: alpha-claim
```

## 6. etcd 백업

ETCD 관련 옵션들 확인하기
```sh
kubectl -n kube-system describe po etcd-controlplane
```

ETCD 백업하기
```sh
etcdctl \
--endpoints=https://192.4.116.9:2379 \
--cacert=/etc/kubernetes/pki/etcd/ca.crt \
--cert=/etc/kubernetes/pki/etcd/server.crt \
--key=/etc/kubernetes/pki/etcd/server.key \
snapshot save  /opt/etcd-backup.db
```

## 7. Secret을 사용하는 Pod 생성

secrets 확인하기
```sh
# kubectl get secrets -n admin1401 
NAME             TYPE     DATA   AGE
dotfile-secret   Opaque   1      29m
```

secrets 확인하기
```sh
# kubectl describe secrets -n admin1401 dotfile-secret 
Name:         dotfile-secret
Namespace:    admin1401
Labels:       <none>
Annotations:  <none>

Type:  Opaque

Data
====
.secret-file:  11 bytes
```

기본 Pod 정의파일 생성하기
```sh
kubectl run secret-1401 -n admin1401 --image=busybox --dry-run=client -o yaml --command -- sleep 20 > p.yaml
``` 

기본 Pod 정의파일과 K8S docs를 참고해서 Pod 정의파일 작성하기
```yaml
apiVersion: v1
kind: Pod
metadata:
  labels:
    run: secret-1401
  name: secret-1401
  namespace: admin1401
spec:
  containers:
  - command:
    - sleep
    - "4800"
    image: busybox
    name: secret-admin
    volumeMounts:
    - name: secret-volume
      mountPath: "/etc/secret-volume"
      readOnly: true
  volumes:
  - name: secret-volume
    secret:
      secretName: dotfile-secret
```

정의파일을 사용해서 Pod 생성하기
```
kubectl apply -f p.yaml
```
