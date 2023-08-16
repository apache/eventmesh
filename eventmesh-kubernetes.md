## Environment

### Prerequisite

- golang

### Install

- [Install the Operator SDK](https://v1-5-x.sdk.operatorframework.io/docs/installation/)

## Build Operator:

- Creating an Operator Project

```
operator-sdk init --domain=yourdomain.com --repo=github.com/apache/eventmesh
```

- Custom Resource

```
operator-sdk create api --group=EventMeshGourp --version=v1 --kind=EventMeshOperator
```

- Implementing controller logic

```
Implement the controller logic in the controllers/controller.go file.
```

- Build the Operator image and push it to the container image repository.

```
operator-sdk build yourrepo/EventMeshOperator
docker push yourrepo/EventMeshOperator
```

## EventMesh Components

- EventMesh-runtime: 核心组件，运行时模块
  
- EventMesh-jdk: 支持HTTP、TCP、GRPC协议
  
- EventMesh-connectors: 连接器，连接插件
  
- EventMesh-storage: 存储模块

- EventMesh-workflow: EventMesh工作流

- EventMesh-dashboard: EventMesh仪表盘

...