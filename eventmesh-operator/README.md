## **EventMesh Operator Quick Start**

### Preparing:
- docker
- golang (version 1.19)
- kubernetes(kubectl)   
  **Note:** There is some compatibility between kubernetes an docker, please check the version compatibility between them and download the corresponding version to ensure that they work properly together.  
  
### Quick Start

#### Deployment

1. Go to the eventmesh-operator directory.
```shell
cd eventmesh-operator
```

2. Install CRD into the specified k8s cluster.
```shell
make install
```

If you get error eventmesh-operator/bin/controller-gen: No such file or directory  
Run the following command:
```shell
# download controller-gen locally if necessary.
make controller-gen
# download kustomize locally if necessary.
make kustomize
```

Success  
```shell
# run the following command to view crds information:
kubectl get crds

NAME                                      CREATED AT
connectors.eventmesh-operator.eventmesh   2023-11-28T01:35:21Z
runtimes.eventmesh-operator.eventmesh     2023-11-28T01:35:21Z
```

3. Create and delete CRs:     
   Custom resource objects are located at: /config/samples    
   When deleting CR, simply replace create with delete.  
```shell
# Create CR for eventmesh-runtime、eventmesh-connector-rocketmq,Creating a clusterIP lets eventmesh-runtime communicate with other components.
make deploy

#success:
configmap/runtime-config created
runtime.eventmesh-operator.eventmesh/eventmesh-runtime created
service/runtime-cluster-service created
configmap/connector-rocketmq-config created
connectors.eventmesh-operator.eventmesh/connector-rocketmq created

# View the created Service.
kubectl get service
NAME                      TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)     AGE
runtime-cluster-service   ClusterIP   10.109.209.72   <none>        10000/TCP   17s

# Delete CR
make uninstall
```

4. Run eventmesh-operator create pods
```shell
make run
# success run log
go fmt ./...
go vet ./...
go run ./main.go
INFO    controller-runtime.metrics      Metrics server is starting to listen    {"addr": ":9020"}
INFO    setup   starting manager
INFO    Starting server {"kind": "health probe", "addr": "[::]:8081"}
INFO    Starting server {"path": "/metrics", "kind": "metrics", "addr": "[::]:9020"}
INFO    runtime         Creating a new eventMeshRuntime StatefulSet.    {"StatefulSet.Namespace": "default", "StatefulSet.Name": "eventmesh-runtime-0-a"}
INFO    connector       Creating a new Connector StatefulSet.   {"StatefulSet.Namespace": "default", "StatefulSet.Name": "connector-rocketmq"}
INFO    runtime         Successful reconciliation!
INFO    connector       Successful reconciliation!



# After the pods are successfully started, run the following command to view pods.
kubectl get pods
NAME                      READY   STATUS    RESTARTS   AGE
connector-rocketmq-0      1/1     Running   0          12m
eventmesh-runtime-0-a-0   1/1     Running   0          12m

kubectl get pods -o wide
NAME                      READY   STATUS    RESTARTS   AGE   IP            NODE       NOMINATED NODE   READINESS GATES
connector-rocketmq-0      1/1     Running   0          13m   10.244.0.21   minikube   <none>           <none>
eventmesh-runtime-0-a-0   1/1     Running   0          13m   10.244.0.20   minikube   <none>           <none>
```
