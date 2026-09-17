# Deploying EventMesh on Kubernetes

Plain manifests for running the EventMesh runtime (StatefulSet) and the
connector-runtime (Deployment) on Kubernetes, wrapped with
[kustomize](https://kustomize.io). Helm packaging is a planned follow-up; the
operator path from the legacy `master` branch (`eventmesh-operator/`, issue
#3327) is not ported to the new architecture yet.

## Quick start

```shell
# 1. Point conf at your real broker (edit runtime-configmap.yaml):
#    eventMesh.server.kafka.namesrvAddr / eventMesh.server.rocketmq.namesrvAddr
# 2. Set a real admin token (runtime-secret.yaml) -- the admin API is
#    FAIL-CLOSED without -Deventmesh.admin.token.
# 3. Apply:
kubectl apply -k .
```

The runtime Service exposes `eventmesh-runtime.eventmesh-system:10105`
(traffic) and `:10106` (admin). Verify:

```shell
kubectl -n eventmesh-system get pods
TOKEN=$(kubectl -n eventmesh-system get secret eventmesh-admin-token -o jsonpath='{.data.token}' | base64 -d)
kubectl -n eventmesh-system run curl --rm -it --image=curlimages/curl --   curl -s -H "Authorization: Bearer $TOKEN"   http://eventmesh-runtime.eventmesh-system.svc.cluster.local:10106/admin/health
```

## What each piece does

| File | Purpose |
| --- | --- |
| `namespace.yaml` | `eventmesh-system` namespace |
| `runtime-configmap.yaml` | `eventmesh.properties` (storage endpoints) — subPath-mounted into `conf/` |
| `runtime-secret.yaml` | Admin bearer token (example; replace before use) |
| `runtime-statefulset.yaml` | Runtime pods: ports, probes on `/admin/health`, non-root (uid 10001), PVC for the RocksDB offset store |
| `runtime-service.yaml` | Stable DNS for traffic (10105) + admin (10106) |
| `connector-configmap.yaml` | Connector topology as `CONNECTOR_OPTS` (edit + rollout restart) |
| `connector-deployment.yaml` | Connector-runtime pods (non-root uid 10002, process liveness probe) |

## Scaling semantics

- Default `-Deventmesh.delivery.topology=LOCAL_STICKY_PULL`: every instance
  polls every partition of every subscribed topic. Works per-instance with no
  coordination, but instances duplicate consumption.
- `PARTITION_OWNED_PULL` (no duplicate consumption, fencing via a meta store):
  requires shared coordination. Add to `JAVA_OPTS` in the StatefulSet:

  ```yaml
  value: "-Deventmesh.admin.token=$(EVENTMESH_ADMIN_TOKEN) -XX:MaxRAMPercentage=70 -Deventmesh.meta.type=nacos -Deventmesh.meta.addr=<nacos-host>:8848 -Deventmesh.offset.meta=true"
  ```

  and scale `replicas`. See
  [deployment docs](../../docs/feature/deployment.md) for the coordination
  keys.

## Storage backends

The image ships the memory (default, zero-dependency) / kafka / rocketmq / rocketmq5
storage plugins. Switch with `EVENTMESH_STORAGE_TYPE` on the runtime container
and point `eventMesh.server.<backend>.namesrvAddr` in the ConfigMap at the
in-cluster service (e.g. Strimzi Kafka bootstrap or a RocketMQ name server
Service).
