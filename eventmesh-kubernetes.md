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

- EventMesh-runtime: Core Components, Runtime Modules
  
- EventMesh-jdk: Supports HTTP, TCP, GRPC protocols
  
- EventMesh-connectors: Connectors, Connecting Inserts
  
- EventMesh-storage: Storage Module

- EventMesh-workflow: EventMesh Workflow

- EventMesh-dashboard: EventMesh Dashboard

...