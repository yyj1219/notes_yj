# 1

## 문제

```
Deploy a pod named nginx-pod using the nginx:alpine image.

Once done, click on the Next Question button in the top right corner of this panel. You may navigate back and forth freely between all questions. Once done with all questions, click on End Exam. Your work will be validated at the end and score shown. Good Luck!
```

## 답안

```
kubectl run nginx-pod --image=nginx:alpine
```

# 2

## 문제

```
Deploy a messaging pod using the redis:alpine image with the labels set to tier=msg.
```

## 답안

```
kubectl run messaging --image=redis:alpine -l tier=msg
```

# 3

## 문제

```
Create a namespace named apx-x9984574.
```

## 답안

```
kubectl create namespace apx-x9984574
```

# 4

## 문제

```
Get the list of nodes in JSON format and store it in a file at /opt/outputs/nodes-z3444kd9.json
```

## 답안

```
kubectl get nodes -o json > /opt/outputs/nodes-z3444kd9.json
```

# 5

## 문제

```
Create a service messaging-service to expose the messaging application within the cluster on port 6379.

Use imperative commands.
```

## 답안

```
kubectl expose pod messaging --port=6379 --name messaging-service
```

# 6

## 문제

```
Create a deployment named hr-web-app using the image kodekloud/webapp-color with 2 replicas.
```

## 답안

```
kubectl create deployment hr-web-app --image=kodekloud/webapp-color --replicas=2
```

# 7

## 문제

```
Create a static pod named static-busybox on the controlplane node that uses the busybox image and the command sleep 1000.
```

## 답안

```
kubectl run --restart=Never --image=busybox static-busybox --dry-run=client -oyaml --command -- sleep 1000 > /etc/kubernetes/manifests/static-busybox.yaml
```

# 8

## 문제

```
Create a POD in the finance namespace named temp-bus with the image redis:alpine.
```

## 답안

```
kubectl run temp-bus --image=redis:alpine --namespace=finance --restart=Never
```

# 9

## 문제

```
A new application orange is deployed. There is something wrong with it. Identify and fix the issue.
```

## 답안

```
kubectl describe po orange
```

```
 Command:
      sh
      -c
      sleeeep 2;
```

```
kubectl edit po orange
```

# 10

## 문제

```
Expose the hr-web-app as service hr-web-app-service application on port 30082 on the nodes on the cluster.

The web application listens on port 8080.
```

## 답안

```
kubectl expose deployment hr-web-app --type=NodePort --port=8080 --name=hr-web-app-service --dry-run=client -o yaml > hr-web-app-service.yaml
```

```
```

# 11

## 문제

```
Use JSON PATH query to retrieve the osImages of all the nodes and store it in a file /opt/outputs/nodes_os_x43kj56.txt.

The osImages are under the nodeInfo section under status of each node.
```

## 답안

```
kubectl get nodes -o jsonpath='{.items[*].status.nodeInfo.osImage}' > /opt/outputs/nodes_os_x43kj56.txt
```

# 12

## 문제

```
Create a Persistent Volume with the given specification: -

Volume name: pv-analytics

Storage: 100Mi

Access mode: ReadWriteMany

Host path: /pv/data-analytics
```

## 답안

```yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: pv-analytics
spec:
  capacity:
    storage: 100Mi
  volumeMode: Filesystem
  accessModes:
    - ReadWriteMany
  hostPath:
      path: /pv/data-analytics
```
