# Killer.sh

# Question 1

```
Task weight: 1%

You have access to multiple clusters from your main terminal through kubectl contexts. Write all those context names into /opt/course/1/contexts.

Next write a command to display the current context into /opt/course/1/context_default_kubectl.sh, the command should use kubectl.

Finally write a second command doing the same thing into /opt/course/1/context_default_no_kubectl.sh, but without the use of kubectl.
```

답안 1
```sh
kubectl config get-contexts --no-headers | awk '{print $2}' > /opt/course/1/contexts
```
아래와 같은 컨텐츠가 확인되어야 한다.
```
# Question /opt/course/1/contexts
k8s-c1-H
k8s-c2-AC
k8s-c3-CCC
```

답안 2
```sh
echo "kubectl config current-context" > /opt/course/1/context_default_kubectl.sh
```

답안 3
```sh
echo "cat ~/.kube/config | grep current" > /opt/course/1/context_default_no_kubectl.sh
```
# Question 2

```
Task weight: 3%

Use context: kubectl config use-context k8s-c1-H

Create a single Pod of image httpd:2.4.41-alpine in Namespace default. The Pod should be named pod1 and the container should be named pod1-container. This Pod should only be scheduled on a controlplane node, do not add new labels any nodes.
```

controlplane 노드와 레이블을 확인합니다.
```sh
kubectl get nodes --show-labels | grep control
```

controlplane 노드의 taint를 확인합니다.
```sh
kubectl describe node cluster1-controlplane1 | grep -i taint
```
```
Taints:    node-role.kubernetes.io/control-plane:NoSchedule
```
```sh
kubectl run -n default pod1 --image=httpd:2.4.41-alpine $dr > 2.yml
```

nodeName 옵션으로 노드를 설정하는 방법
```yaml
apiVersion: v1
kind: Pod
metadata:
spec:
  nodeName: cluster1-controlplane1
  containers:
  - image: httpd:2.4.41-alpine
    name: pod1-container
```

nodeSelector 옵션으로 노드를 설정하는 방법
```yaml
apiVersion: v1
kind: Pod
metadata:
spec:
  nodeName: cluster1-controlplane1
  containers:
  - image: httpd:2.4.41-alpine
    name: pod1-container
  tolerations:
  - effect: NoSchedule
    key: node-role.kubernetes.io/control-plane
  nodeSelector:
    node-role.kubernetes.io/control-plane: ""
```

# Question 3 - StatefulSet 문제로 건너뜀

```
Task weight: 1%

Use context: kubectl config use-context k8s-c1-H

There are two Pods named o3db-* in Namespace project-c13. C13 management asked you to scale the Pods down to one replica to save resources.
```
파드 확인하기
```sh
kubectl get po -n project-c13 -o wide | grep o3db-
```

# Question 4 - LivenessProbe, ReadinessProbe 문제로 건너뜀

```
Task weight: 4%

Use context: kubectl config use-context k8s-c1-H

Do the following in Namespace default. Create a single Pod named ready-if-service-ready of image nginx:1.16.1-alpine. Configure a LivenessProbe which simply executes command true. Also configure a ReadinessProbe which does check if the url http://service-am-i-ready:80 is reachable, you can use wget -T2 -O- http://service-am-i-ready:80 for this. Start the Pod and confirm it isn't ready because of the ReadinessProbe.

Create a second Pod named am-i-ready of image nginx:1.16.1-alpine with label id: cross-server-ready. The already existing Service service-am-i-ready should now have that second Pod as endpoint.

Now the first Pod should be in ready state, confirm that.
```

# Question 5

```
Task weight: 1%

Use context: kubectl config use-context k8s-c1-H

There are various Pods in all namespaces. Write a command into /opt/course/5/find_pods.sh which lists all Pods sorted by their AGE (metadata.creationTimestamp).

Write a second command into /opt/course/5/find_pods_uid.sh which lists all Pods sorted by field metadata.uid. Use kubectl sorting for both commands.
```

```sh
echo "kubectl get pods -A --sort-by=.metadata.creationTimestamp" > /opt/course/5/find_pods.sh
```

```sh
echo "kubectl get pods -A --sort-by=.metadata.uid" > /opt/course/5/find_pods_uid.sh
```

# Question 6

```
Task weight: 8%

Use context: kubectl config use-context k8s-c1-H

Create a new PersistentVolume named safari-pv. It should have a capacity of 2Gi, accessMode ReadWriteOnce, hostPath /Volumes/Data and no storageClassName defined.

Next create a new PersistentVolumeClaim in Namespace project-tiger named safari-pvc . It should request 2Gi storage, accessMode ReadWriteOnce and should not define a storageClassName. The PVC should bound to the PV correctly.

Finally create a new Deployment safari in Namespace project-tiger which mounts that volume at /tmp/safari-data. The Pods of that Deployment should be of image httpd:2.4.41-alpine.
```

K8S docs에서 `pvc hostPath`로 검색한 페이지에서 나오는 내용을 6.yml에 붙여넣기
```sh
vi 6.yml
```

deployment 생성문을 만들어서 6.yml에 추가하기
```sh
kubectl create deployment -n project-tiger safari --image=httpd:2.4.41-alpine $dr >> 6.yml
```

6.yml 수정
```yaml
apiVersion: v1
kind: PersistentVolume
metadata: 
  name: safari-pv
spec:
  capacity:
    storage: 2Gi
  accessModes:
    - ReadWriteOnce
  hostPath:
    path: "/Volumes/Data"
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: safari-pvc
  namespace: project-tiger
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 2Gi
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: safari
  namespace: project-tiger
spec:
  replicas: 1
  selector:
    matchLabels:
      app: safari
  template:
    metadata:
      labels:
        app: safari
    spec:
      volumes:
      - name: safari-pvc
        persistentVolumeClaim:
          claimName: safari-pvc
      containers:
      - name: httpd
        image: httpd:2.4.41-alpine
        volumeMounts:
        - mountPath: "/tmp/safari-data"
          name: safari-pvc
```

# Question 7

```
Task weight: 1%

Use context: kubectl config use-context k8s-c1-H

The metrics-server has been installed in the cluster. Your college would like to know the kubectl commands to:

show Nodes resource usage
show Pods and their containers resource usage
Please write the commands into /opt/course/7/node.sh and /opt/course/7/pod.sh.
```

```sh
echo "kubectl top node" > /opt/course/7/node.sh
```

```sh
echo "kubectl top pods --containers=true" > /opt/course/7/pod.sh
```

# Question 8

```
Task weight: 2%

Use context: kubectl config use-context k8s-c1-H

Ssh into the controlplane node with ssh cluster1-controlplane1. Check how the controlplane components kubelet, kube-apiserver, kube-scheduler, kube-controller-manager and etcd are started/installed on the controlplane node. Also find out the name of the DNS application and how it's started/installed on the controlplane node.

Write your findings into file /opt/course/8/controlplane-components.txt. The file should be structured like:
```
```
# /opt/course/8/controlplane-components.txt
kubelet: [TYPE]
kube-apiserver: [TYPE]
kube-scheduler: [TYPE]
kube-controller-manager: [TYPE]
etcd: [TYPE]
dns: [TYPE] [NAME]
```
```
Choices of [TYPE] are: not-installed, process, static-pod, pod
```

```sh
ssh cluster1-controlplane1
```

파드 이름 끝부분에 hostname이 붙어있는 파드들은 static-pod인 것이 확인됨
```sh
root@cluster1-controlplane1# kubectl get po -n kube-system
```

```sh
vi /opt/course/8/controlplane-components.txt
kubelet: process
kube-apiserver: static-pod
kube-scheduler: static-pod
kube-controller-manager: static-pod
etcd: static-pod
dns: pod coredns
```

# Question 9

```
Task weight: 5%

Use context: kubectl config use-context k8s-c2-AC

Ssh into the controlplane node with ssh cluster2-controlplane1. Temporarily stop the kube-scheduler, this means in a way that you can start it again afterwards.

Create a single Pod named manual-schedule of image httpd:2.4-alpine, confirm it's created but not scheduled on any node.

Now you're the scheduler and have all its power, manually schedule that Pod on node cluster2-controlplane1. Make sure it's running.

Start the kube-scheduler again and confirm it's running correctly by creating a second Pod named manual-schedule2 of image httpd:2.4-alpine and check if it's running on cluster2-node1.
```

controlplane 노드 찾기
```sh
kubectl get nodes
```
controlplane 노드에서 statics-pod인 scheduler 파드를 일시적으로 kill하기
```sh
ssh cluster2-controlplane1
root@cluster2-controlplane1# cd /etc/kubernetes/manifests
root@cluster2-controlplane1# mv kube-scheduler.yaml /tmp
```
첫번째 파드 생성하고, Pending에서 멈춰있는 상태 확인하기
```sh
kubectl run manual-schedule --image=httpd:2.4-alpine

kubectl get pod manual-schedule -o wide
```
매뉴얼하게 파드 스케줄하기
```sh
kubectl get pod manual-schedule -o yaml > 9.yaml

vi 9.yaml
```
scheduler는 오직 파드가 정의될 노드이름만을 정하는 것이기 때문에 scheduler 파드가 죽어 있어도 nodeName을 지정하면 해당 노드에 파드가 배포된다.
```yaml
apiVersion: v1
kind: Pod
metadata:
  labels:
    run: manual-schedule
spec:
  nodeName: cluster2-controlplane1 # 노드 지정
  containers:
  - image: httpd:2.4-alpine
```

```sh
kubectl replace --force -f 9.yaml
```

controlplane 노드에서 scheduler 파드를 다시 시작하기
```sh
ssh cluster2-controlplane1
root@cluster2-controlplane1# mv /tmp/kube-scheduler.yaml /etc/kubernetes/manifests
```
새로운 파드 배포하기
```
kubectl run manual-schedule2 --image=httpd:2.4-alpine
```

# Question 10

```
Task weight: 6%

Use context: kubectl config use-context k8s-c1-H

Create a new ServiceAccount processor in Namespace project-hamster. Create a Role and RoleBinding, both named processor as well. These should allow the new SA to only create Secrets and ConfigMaps in that Namespace.
```

ServiceAccount 생성
```sh
kubectl -n project-hamster create serviceaccount processor
```
Role 생성
```sh
kubectl -n project-hamster create role processor --verb=create --resource=secrets,configmaps
```
RoleBinding 생성
```sh
kubectl -n project-hamster create rolebinding processor --role=processor --serviceaccount=project-hamster:processor
```
권한 체크
```
kubectl -n project-hamster auth can-i create secret \
  --as system:serviceaccount:project-hamster:processor

kubectl -n project-hamster auth can-i get secret \
  --as system:serviceaccount:project-hamster:processor

kubectl -n project-hamster auth can-i create configmap \
  --as system:serviceaccount:project-hamster:processor
```

# Question 11 - DaemonSet 생성 문제로 건너뜀

```
Task weight: 4%

Use context: kubectl config use-context k8s-c1-H

Use Namespace project-tiger for the following. Create a DaemonSet named ds-important with image httpd:2.4-alpine and labels id=ds-important and uuid=18426a0b-5f59-4e10-923f-c0e078e82462. The Pods it creates should request 10 millicore cpu and 10 mebibyte memory. The Pods of that DaemonSet should run on all nodes, also controlplanes.
```

```sh
```

# Question 12 - podAntiAffinity 옵션 사용 문제로 건너뜀

```
Task weight: 6%

Use context: kubectl config use-context k8s-c1-H

Use Namespace project-tiger for the following. Create a Deployment named deploy-important with label id=very-important (the Pods should also have this label) and 3 replicas. It should contain two containers, the first named container1 with image nginx:1.17.6-alpine and the second one named container2 with image kubernetes/pause.

There should be only ever one Pod of that Deployment running on one worker node. We have two worker nodes: cluster1-node1 and cluster1-node2. Because the Deployment has three replicas the result should be that on both nodes one Pod is running. The third Pod won't be scheduled, unless a new worker node will be added.

In a way we kind of simulate the behaviour of a DaemonSet here, but using a Deployment and a fixed number of replicas.
```

```sh
kubectl -n project-tiger create deployment deploy-important --image=nginx:1.17.6-alpine --replicas=3 $dr > 12.yml

vi 12.yml
```
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    id: very-important
  name: deploy-important
  namespace: project-tiger
spec:
  replicas: 3
  selector:
    matchLabels:
      id: very-important
  template:
    metadata:
      labels:
        id: very-important
    containers:
    - image: nginx:1.17.6-alpine
      name: container1
    - image: nkubernetes/pause
      name: container2
    affinity:                                             # add
      podAntiAffinity:                                    # add
        requiredDuringSchedulingIgnoredDuringExecution:   # add
        - labelSelector:                                  # add
            matchExpressions:                             # add
            - key: id                                     # add
            operator: In                                # add
            values:                                     # add
            - very-important                            # add
        topologyKey: kubernetes.io/hostname             # add
```

# Question 13 - multi Pod 인데 volume과 env, command 포함해서 건너뜀

```
Task weight: 4%

Use context: kubectl config use-context k8s-c1-H

Create a Pod named multi-container-playground in Namespace default with three containers, named c1, c2 and c3. There should be a volume attached to that Pod and mounted into every container, but the volume shouldn't be persisted or shared with other Pods.

Container c1 should be of image nginx:1.17.6-alpine and have the name of the node where its Pod is running available as environment variable MY_NODE_NAME.

Container c2 should be of image busybox:1.31.1 and write the output of the date command every second in the shared volume into file date.log. You can use while true; do date >> /your/vol/path/date.log; sleep 1; done for this.

Container c3 should be of image busybox:1.31.1 and constantly send the content of file date.log from the shared volume to stdout. You can use tail -f /your/vol/path/date.log for this.

Check the logs of container c3 to confirm correct setup.
```

```sh
```

# Question 14

```
Task weight: 2%

Use context: kubectl config use-context k8s-c1-H

You're ask to find out following information about the cluster k8s-c1-H :

1. How many controlplane nodes are available?
2. How many worker nodes are available?
3. What is the Service CIDR?
4. Which Networking (or CNI Plugin) is configured and where is its config file?
5. Which suffix will static pods have that run on cluster1-node1?

Write your answers into file /opt/course/14/cluster-info, structured like this:

# /opt/course/14/cluster-info
1: [ANSWER]
2: [ANSWER]
3: [ANSWER]
4: [ANSWER]
5: [ANSWER]
```

Service CIDR는 apiserver에 service-cluster-ip-range 옵션을 확인합니다.
```sh
root@cluster1-controlplane# ps -ef | grep apiserver | grep range
```

# Question 15

```
Task weight: 3%

Use context: kubectl config use-context k8s-c2-AC

Write a command into /opt/course/15/cluster_events.sh which shows the latest events in the whole cluster, ordered by time (metadata.creationTimestamp). Use kubectl for it.

Now kill the kube-proxy Pod running on node cluster2-node1 and write the events this caused into /opt/course/15/pod_kill.log.

Finally kill the containerd container of the kube-proxy Pod on node cluster2-node1 and write the events into /opt/course/15/container_kill.log.

Do you notice differences in the events both actions caused?
```

클러스터에 모든 이벤트 확인
```sh
echo "kubectl get events -A --sort-by=.metadata.creationTimestamp" > /opt/course/15/cluster_events.sh
```
cluster2-node1 노드에서 kube-proxy 컨테이너를 찾아서 삭제하기 -> 삭제해도 자동으로 다시 kube-proxy 컨테이너 시작함
```sh
ssh cluster2-node1
root@cluster2-node1# crictl ps | grep proxy
root@cluster2-node1# crictl rm -f 1e020b43c4423
```
kube-proxy 컨테이너 삭제와 복구 관련 이벤트 로그를 파일에 쓰기
```
kubectl get events -A --sort-by=.metadata.creationTimestamp | grep proxy > /opt/course/15/pod_kill.log
```


# Question 16

```
Task weight: 2%

Use context: kubectl config use-context k8s-c1-H

Write the names of all namespaced Kubernetes resources (like Pod, Secret, ConfigMap...) into /opt/course/16/resources.txt.

Find the project-* Namespace with the highest number of Roles defined in it and write its name and amount of Roles into /opt/course/16/crowded-namespace.txt.
```

```sh
kubectl api-resources --namespaced -o name > /opt/course/16/resources.txt
```

```sh
# project-* 네임스페이스에 모든 role 이름 확인
kubectl get role -A | grep project- | awk '{print $1}' | sort | uniq
# 네임스페이스의 role 개수 확인 
kubectl get role -A | grep project- | grep project-c14 | wc -l
```

```sh
cat /opt/course/16/crowded-namespace.txt
project-c14 300
```

# Question 17

```
Task weight: 3%

Use context: kubectl config use-context k8s-c1-H

In Namespace project-tiger create a Pod named tigers-reunite of image httpd:2.4.41-alpine with labels pod=container and container=pod. Find out on which node the Pod is scheduled. Ssh into that node and find the containerd container belonging to that Pod.

Using command crictl:

1. Write the ID of the container and the info.runtimeType into /opt/course/17/pod-container.txt
2. Write the logs of the container into /opt/course/17/pod-container.log
```

```sh
kubectl run -n project-tiger tigers-reunite --image=httpd:2.4.41-alpine --labels='pod=container,container=pod'
```

```sh
ssh cluster1-node1
# 컨테이너 아이디 확인
root@cluster1-node1# crictl ps | grep tigers-reunite
# 컨테이너 런타임 확인
root@cluster1-node1# crictl info b01edbe6f89ed | grep -i runtimetype
```

```sh
ssh cluster1-node1 `crictl logs b01edbe6f89ed` &> /opt/course/17/pod-container.log
```

# Question 18

```
Task weight: 8%

Use context: kubectl config use-context k8s-c3-CCC

There seems to be an issue with the kubelet not running on cluster3-node1. Fix it and confirm that cluster has node cluster3-node1 available in Ready state afterwards. You should be able to schedule a Pod on cluster3-node1 afterwards.

Write the reason of the issue into /opt/course/18/reason.txt.
```
kubelet 이 inactive 이다.
```sh
root@cluster3-node1# systemctl status kubelet.service
```
kubelet 서비스를 다시 시작하면 /usr/local/bin/kubelet을 못 찾아서 실패한 것이 확인된다.
```sh
root@cluster3-node1# systemctl start kubelet.service
root@cluster3-node1# systemctl status kubelet.service
```
kubelet 위치는 /usr/bin/kubelet 이다.
```sh
root@cluster3-node1# which kubelet
/usr/bin/kubelet
```
kubelet 서비스의 경로를 수정한다. 서비스 파일은 `systemctl status kubelet` 명령에서 Drop-In 부분에서 확인되는 conf 파일이다.

```sh
# kubelet 이 config 파일 /var/lib/kubelet/config.yaml을 로드하지 못 읽어서 실패한 것을 확인
root@cluster3-node1# journalctl -u kubelet
```

# Question 19 - 건너뜀

```
Task weight: 3%

NOTE: This task can only be solved if questions 18 or 20 have been successfully implemented and the k8s-c3-CCC cluster has a functioning worker node

Use context: kubectl config use-context k8s-c3-CCC

Do the following in a new Namespace secret. Create a Pod named secret-pod of image busybox:1.31.1 which should keep running for some time.

There is an existing Secret located at /opt/course/19/secret1.yaml, create it in the Namespace secret and mount it readonly into the Pod at /tmp/secret1.

Create a new Secret in Namespace secret called secret2 which should contain user=user1 and pass=1234. These entries should be available inside the Pod's container as environment variables APP_USER and APP_PASS.

Confirm everything is working.
```

```sh
```

# Question 20 - worker 노드 업그레이드 후 join 시키기

```
Task weight: 10%

Use context: kubectl config use-context k8s-c3-CCC

Your coworker said node cluster3-node2 is running an older Kubernetes version and is not even part of the cluster. Update Kubernetes on that node to the exact version that's running on cluster3-controlplane1. Then add this node to the cluster. Use kubeadm for this.
```

```sh
```

# Question 21

```
Task weight: 2%

Use context: kubectl config use-context k8s-c3-CCC

Create a Static Pod named my-static-pod in Namespace default on cluster3-controlplane1. It should be of image nginx:1.16-alpine and have resource requests for 10m CPU and 20Mi memory.

Then create a NodePort Service named static-pod-service which exposes that static Pod on port 80 and check if it has Endpoints and if it's reachable through the cluster3-controlplane1 internal IP address. You can connect to the internal node IPs from your main terminal.
```

```sh
kubectl -n default my-static-pod --image=nginx:1.16-alpine $dr > my-static-pod.yaml
```
```yaml
# my-static-pod.yaml
apiVersion: v1
kind: Pod
metadata:
  name: my-static-pod
  namespace: default
  labels:
    run: my-static-pod
spec:
  name: my-static-pod
  image: nginx:1.16-alpine
  resources:
    requests:
      memory: "20Mi"
      cpu: "10m"
```
my-static-pod.yaml 파일을 cluster3-controlplane1 노드의 /etc/kubernetes/manifests 경로에 두고,  static-pod가 생성되길 기다린다.
```sh
# 서비스 생성 기본 파일 작성
kubectl -n default expose pod my-static-pod-cluster3-controlplane1 --name=static-pod-service --type=NodePort --port=80 $dr > 21.yml
```
```yaml
apiVersion: v1
kind: Service
metadata:
  labels:
    run: my-static-pod
    name: static-pod-service
    namespace: default
spec:
  ports:
  - port: 80
    protocol: TCP
    targetPort: 80
  type: NodePort
  selector: 
    run: my-static-pod
```

```sh
# NodePort 번호 확인
kubectl describe svc static-pod-service
# cluster3-controlplane1 노드 InternalIP 확인
kubectl get nodes -o wide
```

```sh
curl 192.168.100.31:31183
```

# Question 22

```
Task weight: 2%

Use context: kubectl config use-context k8s-c2-AC

Check how long the kube-apiserver server certificate is valid on cluster2-controlplane1. Do this with openssl or cfssl. Write the exipiration date into /opt/course/22/expiration.

Also run the correct kubeadm command to list the expiration dates and confirm both methods show the same date.

Write the correct kubeadm command that would renew the apiserver server certificate into /opt/course/22/kubeadm-renew-certs.sh.
```

```sh
root@cluster2-controlplane# openssl x509  -noout -text -in /etc/kubernetes/pki/apiserver.crt | grep Validity -A2
```

# Question 23 - 건너뜀

```
Task weight: 2%

Use context: kubectl config use-context k8s-c2-AC

Node cluster2-node1 has been added to the cluster using kubeadm and TLS bootstrapping.

Find the "Issuer" and "Extended Key Usage" values of the cluster2-node1:

kubelet client certificate, the one used for outgoing connections to the kube-apiserver.
kubelet server certificate, the one used for incoming connections from the kube-apiserver.
Write the information into file /opt/course/23/certificate-info.txt.

Compare the "Issuer" and "Extended Key Usage" fields of both certificates and make sense of these.
```

```sh
```

# Question 24

```
Task weight: 9%

Use context: kubectl config use-context k8s-c1-H

There was a security incident where an intruder was able to access the whole cluster from a single hacked backend Pod.

To prevent this create a NetworkPolicy called np-backend in Namespace project-snake. It should allow the backend-* Pods only to:

connect to db1-* Pods on port 1111
connect to db2-* Pods on port 2222
Use the app label of Pods in your policy.

After implementation, connections from backend-* Pods to vault-* Pods on port 3333 should for example no longer work.
```

K8S docs에서 복사해서 수정한 내용으로 작성
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: np-backend
  namespace: project-snake
spec:
  podSelector:
    matchLabels:
      app: backend
  policyTypes:
    - Egress
  egress:
    - to:
        - podSelector:
            matchLabels:
              app: db1
      ports:
        - protocol: TCP
          port: 1111
    - to:
        - podSelector:
            matchLabels:
              app: db2
      ports:
        - protocol: TCP
          port: 2222
```

# Question 25

```
Task weight: 8%

Use context: kubectl config use-context k8s-c3-CCC

Make a backup of etcd running on cluster3-controlplane1 and save it on the controlplane node at /tmp/etcd-backup.db.

Then create a Pod of your kind in the cluster.

Finally restore the backup, confirm the cluster is still working and that the created Pod is no longer with us.
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```

# Question 

```
```

```sh
```
