# EventMesh control plane

> **Status:** Living document. Reflects the post-#5296 architecture review state of the
> `develop` branch. Coverage of `DeliveryTopology` (#5293 / #5309) and `SecurityGate`
> (#5304) is the canonical answer to the review question Q2 (2026-09-07). Tracked by #5338.

This page documents the control-plane components that the runtime boot path
wires from configuration. For each component, we list the config key, the
default value, the failure behavior when the key is missing or malformed, and
the ingress route(s) that the component gates.

## 1. DeliveryTopology (#5293, #5309)

Selects how the runtime polls / dispatches events. Two modes:

| Mode | Default? | What it does | What it requires |
| --- | --- | --- | --- |
| `LOCAL_STICKY_PULL` | **Yes** | Every runtime instance polls every partition of every subscribed topic. No Meta store dependency. Backward compatible with pre-#5301 deployments. | None |
| `PARTITION_OWNED_PULL` | No | Each instance acquires ownership of a strict subset of partitions via Meta CAS + fencing. No duplicate consumption across instances. | Meta store (Nacos / etcd / ZK). Code is intact (see `PartitionOwnership`); the wiring follow-up is tracked in #5309. |

### 1.1 Configuration

* **Key:** `eventmesh.delivery.topology`
* **Default:** `LOCAL_STICKY_PULL` (the value is read in
  `EventMeshApplication` via `DeliveryTopology.fromConfig(System.getProperty(...))`).
* **Set via:** JVM `-D` flag (`-Deventmesh.delivery.topology=PARTITION_OWNED_PULL`)
  or the `EVENTMESH_DELIVERY_TOPOLOGY` environment variable (the runtime
  forwards env-var values into system properties at boot).
* **Documented in `eventmesh.properties`:** the property is listed with a
  commented default and an inline explanation of both modes; operators can
  uncomment + edit without leaving the config file.

### 1.2 Failure behavior

* **Missing or blank** -> resolved to `LOCAL_STICKY_PULL` (backward
  compatible). The startup log emits `delivery topology=LOCAL_STICKY_PULL`.
* **Unknown value** (e.g. `PARTITION_OWNER` typo) -> `IllegalArgumentException`
  raised by `DeliveryTopology.fromConfig`. The runtime fails fast at boot;
  the operator sees a clear error in `bin/start.sh` output. A typo must not
  silently degrade to single-instance mode (see `DeliveryTopologyTest`).
* **`PARTITION_OWNED_PULL` without a Meta store** -> `enableCluster` is
  required. The boot path throws if `metaStore` is null when the topology is
  `PARTITION_OWNED_PULL`.

### 1.3 Coverage per ingress route

The delivery topology is referenced from a single code path
(`UniRuntime.poll` -> `ownedPartitions(topic)`), which is the consumer of
`UniIngressService.deliver`. Therefore every ingress path is covered by
construction:

| Ingress route | Path through topology |
| --- | --- |
| HTTP `POST /events/publish` | `UniHttpServer` -> `UniIngressService.publish` -> `Frame` -> `Producer.send` -> `poll loop` -> topology-selected partition set |
| A2A `POST /a2a/tasks/send` | `A2AGatewayHttpHandler` -> `A2AGatewayService` -> `UniIngressService` -> same as above |
| Legacy TCP (`UniTcpServer`) | main path is being phased out (see docs/protocols.md #5341); when active, the TCP server feeds the same `UniIngressService` |
| WebSocket push | passive receiver; not gated by topology (the topology decides *which* partitions to *poll*, not the push path) |
| SSE | passive receiver; not gated by topology |

Coverage is verified by `git grep` of `DeliveryTopology` in the runtime
source tree (only `UniRuntime` and `DeliveryTopology` itself should match in
`eventmesh-runtime/src/main/`).

## 2. SecurityGate (#5304)

`SecurityGate` is the per-request control-plane gate that lives in front of
`UniIngressService` and `A2AGatewayService`. It is composed of:

* **FilterChain** (auth / acl): `TokenAuthFilter`, `SignatureVerifierFilter`,
  `AclFilter`.
* **QuotaManager** (per-tenant): `TenantQuotaManager`.
* **AuditSink**: pluggable (default is `LoggingAuditSink`).

### 2.1 Configuration

* **Key:** `eventmesh.security.gate.*` (see `eventmesh.properties` section
  `eventmesh.security.gate.*` and the per-filter keys).
* **Default:** a no-op gate that allows every request. This is the
  backward-compatible default and is documented as such in
  `docs/eventmesh-configuration.md`.
* **Fail-closed mode:** when `eventmesh.security.gate.failClosed=true`,
  any filter chain exception (auth failed, signature invalid, ACL denied)
  results in a 401/403 response. The default is fail-open (allowed) so a
  misconfigured gate does not silently break a deployment.

### 2.2 Failure behavior

* **Missing or blank** -> no-op gate; every request is allowed. Operators
  who need a real gate must set the per-filter keys.
* **Malformed ACL entry** -> logged at WARN, the request is denied (fail
  safe in the security context), the deny is counted in the gate's metrics
  counter.
* **Audit sink failure** -> logged at ERROR, the request is still allowed
  (audit is best-effort; a failed audit must not take down the data path).

### 2.3 Coverage per ingress route

| Ingress route | Gated by SecurityGate? | Where the gate is invoked |
| --- | --- | --- |
| HTTP `POST /events/publish` | **Yes** | `UniHttpServer` -> `RequestContext` -> `SecurityGate.check(ctx, frame)` -> `UniIngressService.publish` |
| HTTP `POST /events/subscribe` | **Yes** | same chain |
| A2A `POST /a2a/tasks/send` | **Yes** | `A2AGatewayHttpHandler` -> gate -> `A2AGatewayService` -> `UniIngressService` |
| Legacy TCP | **Yes** (gated by TCP-level auth, separate from SecurityGate) | `UniTcpServer` -> `TcpIngressBridge` (the TCP path has its own connection-level auth, see docs/protocols.md) |
| Admin HTTP | **Yes** | `UniAdminServer` -> `UniAdminService` (separate gate, configured under `eventmesh.admin.security.*`) |
| WebSocket push | n/a (passive) | push does not invoke the gate; the gate ran at subscribe time |
| SSE | n/a (passive) | same as WS |
| Connector Runtime | **Yes** | `ConnectorScheduler` -> gate (configured separately) |

## 3. Configuration matrix

| Property | Default | Component | Effect of the default |
| --- | --- | --- | --- |
| `eventmesh.delivery.topology` | `LOCAL_STICKY_PULL` | DeliveryTopology | Single-instance / no Meta dependency |
| `eventmesh.security.gate.failClosed` | `false` | SecurityGate | Backward-compatible allow-all |
| `eventmesh.storage.type` | (commented out) | Storage plugin | Must be set to a supported backend |
| `eventmesh.connector.offset.store` | `memory` | Connector | Offsets not persisted (dev-only) |

## 4. References

* Parent: #5296 (Architecture Review, Q2 / 2026-09-07)
* Tracking: #5338
* DeliveryTopology decision: #5293 (closed via PR #5308, implementing `LOCAL_STICKY_PULL`)
* DeliveryTopology wiring follow-up: #5309 (`PARTITION_OWNED_PULL` wiring)
* SecurityGate: #5304
* Architecture: `docs/eventmesh-architecture.md` (section 4, Security gate)
* Configuration: `docs/eventmesh-configuration.md`
