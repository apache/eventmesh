## Environment

### Prerequisite

- golang

### Install

- [Install the Operator SDK](https://v1-5-x.sdk.operatorframework.io/docs/installation/)

## Build Operator:

- Create an Operator Project

```
operator-sdk init --domain domain1test --repo github.com/xc/eventmesh-operator

Directory Structure:
.
├── Dockerfile
├── Makefile
├── PROJECT
├── README.md
├── config
│   ├── default
│   │   ├── kustomization.yaml
│   │   ├── manager_auth_proxy_patch.yaml
│   │   └── manager_config_patch.yaml
│   ├── manager
│   │   ├── kustomization.yaml
│   │   └── manager.yaml
│   ├── manifests
│   │   └── kustomization.yaml
│   ├── prometheus
│   │   ├── kustomization.yaml
│   │   └── monitor.yaml
│   ├── rbac
│   │   ├── auth_proxy_client_clusterrole.yaml
│   │   ├── auth_proxy_role.yaml
│   │   ├── auth_proxy_role_binding.yaml
│   │   ├── auth_proxy_service.yaml
│   │   ├── kustomization.yaml
│   │   ├── leader_election_role.yaml
│   │   ├── leader_election_role_binding.yaml
│   │   ├── role_binding.yaml
│   │   └── service_account.yaml
│   └── scorecard
│       ├── bases
│       │   └── config.yaml
│       ├── kustomization.yaml
│       └── patches
│           ├── basic.config.yaml
│           └── olm.config.yaml
├── go.mod
├── go.sum
├── hack
│   └── boilerplate.go.txt
└── main.go
```
**Makefile**:  make targets for building and deploying your controller       
**PROJECT**: automatically generated project metadata.     
**go.mod**: a new Go module matching our project, with basic dependencies    
**config/default**: contains for starting the controller in a standard configuration.     
**config/manager**: launch your controllers as pods in the cluster       
**config/rbac**: permissions required to run your controllers under their own service account   

- Create api、resource、controller

```
operator-sdk create api --group grouptest --version v1 --kind EventMeshOperator --resource --controller 

Directory Structure:
.
├── Dockerfile
├── Makefile
├── PROJECT
├── README.md
├── api
│   └── v1
│       ├── eventmeshoperator_types.go
│       ├── groupversion_info.go
│       └── zz_generated.deepcopy.go
├── bin
│   └── controller-gen
├── config
│   ├── crd
│   │   ├── kustomization.yaml
│   │   ├── kustomizeconfig.yaml
│   │   └── patches
│   │       ├── cainjection_in_eventmeshoperators.yaml
│   │       └── webhook_in_eventmeshoperators.yaml
│   ├── default
│   │   ├── kustomization.yaml
│   │   ├── manager_auth_proxy_patch.yaml
│   │   └── manager_config_patch.yaml
│   ├── manager
│   │   ├── kustomization.yaml
│   │   └── manager.yaml
│   ├── manifests
│   │   └── kustomization.yaml
│   ├── prometheus
│   │   ├── kustomization.yaml
│   │   └── monitor.yaml
│   ├── rbac
│   │   ├── auth_proxy_client_clusterrole.yaml
│   │   ├── auth_proxy_role.yaml
│   │   ├── auth_proxy_role_binding.yaml
│   │   ├── auth_proxy_service.yaml
│   │   ├── eventmeshoperator_editor_role.yaml
│   │   ├── eventmeshoperator_viewer_role.yaml
│   │   ├── kustomization.yaml
│   │   ├── leader_election_role.yaml
│   │   ├── leader_election_role_binding.yaml
│   │   ├── role_binding.yaml
│   │   └── service_account.yaml
│   ├── samples
│   │   ├── grouptest_v1_eventmeshoperator.yaml
│   │   └── kustomization.yaml
│   └── scorecard
│       ├── bases
│       │   └── config.yaml
│       ├── kustomization.yaml
│       └── patches
│           ├── basic.config.yaml
│           └── olm.config.yaml
├── controllers
│   ├── eventmeshoperator_controller.go
│   └── suite_test.go
├── go.mod
├── go.sum
├── hack
│   └── boilerplate.go.txt
└── main.go
``` 
**eventmeshoperator_types.go**: custom CRD corresponding struct place.  
**groupversion_info.go**: groupVersion (GV) defines and registers CRD with Scheme.  
**zz_generated.deepcopy.go**: GVR DeepCopy method automatically generated.  
**crd**: the relevant Yaml collection for deploying CRD.  
**default**: a default Yaml collection of the Operator is deployed using Kustomize, which is based on crd, rbac, and manager.  
**manager**: deploy the associated Yaml collection of operators.  
**prometheus**: the Operator runs and monitors the associated Yaml collections.  
**rbac**: Yaml collections associated with RBAC permissions required for Operator deployment.  
**samples**: deploy Yaml for a CR sample.   
**controllers**: developers implement their own logic, and ventmeshoperator_controller.go is the file that completes the control logic.

- Controller TODO
```
func (r *EventMeshOperatorReconciler) Reconcile(ctx context.Context, req ctrl.Request) (ctrl.Result, error) {
        _ = log.FromContext(ctx)

        fmt.Print("Hello!")
        // TODO(user): your logic here

        return ctrl.Result{}, nil
}
```

- Register to CRD:
```
make generate
make manifests
```

- Builder IMG
```
make docker-builder IMG=
make deploy IMG=
```

## EventMesh Components

- EventMesh-runtime: Core Components, Runtime Modules
  
- EventMesh-sdks: Supports HTTP, TCP, GRPC protocols
  
- EventMesh-connectors: Connectors, Connecting Inserts
  
- EventMesh-storage: Storage Module

- EventMesh-workflow: EventMesh Workflow

- EventMesh-dashboard: EventMesh Dashboard

...