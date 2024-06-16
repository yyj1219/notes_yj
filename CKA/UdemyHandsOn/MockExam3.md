# 1

## 질문

```
Create a new service account with the name pvviewer. Grant this Service account access to list all PersistentVolumes in the cluster by creating an appropriate cluster role called pvviewer-role and ClusterRoleBinding called pvviewer-role-binding.
Next, create a pod called pvviewer with the image: redis and serviceAccount: pvviewer in the default namespace.
```

## 답안

```sh
kubectl create serviceaccount pvviewer
```

```sh
kubectl create clusterrole pvviewer-role --resource=persistentvolumes --verb=list
```

```sh
kubectl create clusterrolebinding pvviewer-role-binding --clusterrole=pvviewer-role --serviceaccount=default:pvviewer
```

```yaml
apiVersion: v1
kind: Pod
metadata:
  labels:
    run: pvviewer
  name: pvviewer
spec:
  containers:
  - image: redis
    name: pvviewer
  # Add service account name
  serviceAccountName: pvviewer
```

# 2

## 질문
```
List the InternalIP of all nodes of the cluster. Save the result to a file /root/CKA/node_ips.
Answer should be in the format: InternalIP of controlplane<space>InternalIP of node01 (in a single line)
```

## 답안

```sh
kubectl get nodes -o jsonpath='{.items[*].status.addresses[?(@.type=="InternalIP")].address}' > /root/CKA/node_ips
```

# 3

## 문제

```
Create a pod called multi-pod with two containers.
Container 1, name: alpha, image: nginx
Container 2: name: beta, image: busybox, command: sleep 4800

Environment Variables:
container 1:
name: alpha

Container 2:
name: beta
```

## 답안

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: multi-pod
spec:
  containers:
  - image: nginx
    name: alpha
    env:
    - name: name
      value: alpha
  - image: busybox
    name: beta
    command: ["sleep", "4800"]
    env:
    - name: name
      value: beta
```

# 4

## 문제

```
Create a Pod called non-root-pod , image: redis:alpine

runAsUser: 1000

fsGroup: 2000
```

## 답안

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: non-root-pod
spec:
  securityContext:
    runAsUser: 1000
    fsGroup: 2000
  containers:
  - name: non-root-pod
    image: redis:alpine
```


# 5

## 문제

```
We have deployed a new pod called np-test-1 and a service called np-test-service. Incoming connections to this service are not working. Troubleshoot and fix it.
Create NetworkPolicy, by the name ingress-to-nptest that allows incoming connections to the service over port 80.

Important: Don't delete any current objects deployed.
```

## 답안

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: ingress-to-nptest
  namespace: default
spec:
  podSelector:
    matchLabels:
      run: np-test-1
  policyTypes:
  - Ingress
  ingress:
  - ports:
    - protocol: TCP
      port: 80
```

```sh
kubectl run testpod --image=alpine/curl --rm -it -- curl np-test-service
```

# 6

## 문제

```
Taint the worker node node01 to be Unschedulable. Once done, create a pod called dev-redis, image redis:alpine, to ensure workloads are not scheduled to this worker node. Finally, create a new pod called prod-redis and image: redis:alpine with toleration to be scheduled on node01.

key: env_type, value: production, operator: Equal and effect: NoSchedule
```

## 답안

```
kubectl taint node node01 env_type=production:NoSchedule
```

```
kubectl run dev-redis --image=redis:alpine
```

```
kubectl get pods -o wide
```

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: prod-redis
spec:
  containers:
  - name: prod-redis
    image: redis:alpine
  tolerations:
  - effect: NoSchedule
    key: env_type
    operator: Equal
    value: production 
```

```
kubectl get pods -o wide | grep prod-redis
```

# 7

## 문제

```
Create a pod called hr-pod in hr namespace belonging to the production environment and frontend tier .
image: redis:alpine

Use appropriate labels and create all the required objects if it does not exist in the system already.
```

## 답안

```sh
kubectl create namespace hr
```

```sh
kubectl run hr-pod --image=redis:alpine --namespace=hr --labels=environment=production,tier=frontend
```

# 8

## 문제

```
A kubeconfig file called super.kubeconfig has been created under /root/CKA. There is something wrong with the configuration. Troubleshoot and fix it.
```

## 답안

```
kubectl cluster-info --kubeconfig=/root/CKA/super.kubeconfig
```

# 9

## 문제

```
We have created a new deployment called nginx-deploy. scale the deployment to 3 replicas. Has the replica's increased? Troubleshoot the issue and fix it.
```

## 답안

```
kubectl scale deploy nginx-deploy --replicas=3
```

contro1ler-manager 파드 이상 확인
```
kubectl get pods -n kube-system -o wide
```

contro1ler-manager 파드 이상 상세 확인
```
kubectl describe po kube-contro1ler-manager-controlplane -n kube-system
```

파드 이미지 오류 수정
```
vi /etc/kubernetes/manifests/kube-controller-manager.yaml
```

```
kubectl get deploy
```
