# EventMesh protocols and SDKs

> **Audience:** anyone choosing a wire protocol or SDK. The canonical
> inventory of protocols, server-side plugins, and client SDKs, with
> GA / Beta / Experimental / Legacy status (README table is authoritative).

---

This issue tracks the separation called out in #5296 review question Q5
(2026-09-07): the modern **HTTP + CloudEvents + EventMeshFrame** path must be
the only one downstream code depends on, while the legacy TCP, gRPC, and
OpenMessaging paths must be clearly marked and isolated from the modern SDK
public surface.

## 1. Wire protocols

| Protocol | Status | Wire format | Where it lives | Replacement |
| --- | --- | --- | --- | --- |
| CloudEvents 1.0 over HTTP | **GA** | CloudEvents JSON (binary-mode optional) | runtime ingress / egress (HTTP), `eventmesh-protocol-plugin/eventmesh-protocol-cloudevents` | - |
| EventMeshFrame (internal) | **GA** | `org.apache.eventmesh.common.wire.EventMeshFrame` (Frame architecture) | runtime internal; producer / storage / push path | - |
| A2A (Agent-to-Agent) | **Experimental** | A2A JSON-RPC + SSE | `eventmesh-protocol-plugin/eventmesh-protocol-a2a`, A2A gateway on Runtime | - |
| MeshMessage TCP | **Legacy** | length-prefixed `MeshMessage` bytes | runtime `tcp/` subpackage, `eventmesh-protocol-plugin/eventmesh-protocol-meshmessage/resolver/tcp` | CloudEvents HTTP, or A2A (for agent workloads) |
| gRPC (CloudEvents + EventMeshMessage) | **Beta** | protobuf over HTTP/2 | runtime `transport/grpc`, `eventmesh-protocol-plugin/eventmesh-protocol-meshmessage/resolver/grpc` | CloudEvents HTTP for new clients |
| OpenMessaging API (TCP) | **Legacy** | OMA spec, used by the legacy TCP client only | `eventmesh-sdks/eventmesh-sdk-java/client/tcp/impl/openmessage` | CloudEvents HTTP client |

> **GA** = production-ready and the recommended path. **Beta** = stable but
> the API surface may still shift. **Experimental** = subject to breaking
> change without notice. **Legacy** = still works, but is no longer the
> recommended choice and is being phased out.

## 2. Server-side protocol plugins

The runtime discovers protocol adaptors via the
`org.apache.eventmesh.protocol.api.ProtocolAdaptor` SPI. Active plugins:

* `eventmesh-protocol-plugin/eventmesh-protocol-cloudevents` - **GA**.
  HTTP publish / subscribe over CloudEvents JSON. Required by the modern
  data plane. Do not deprecate.
* `eventmesh-protocol-plugin/eventmesh-protocol-meshmessage` - **Beta** as
  the HTTP / gRPC resolver package, **Legacy** as the TCP resolver
  package. The HTTP and gRPC surfaces remain because they carry
  EventMeshMessage semantics (used by some existing gRPC clients); the TCP
  surface is in maintenance mode and receives only critical bug fixes.
* `eventmesh-protocol-plugin/eventmesh-protocol-a2a` - **Experimental**.
  A2A wire contract; routes tasks through the A2A gateway on Runtime.

The `eventmesh-architecture-guard` module enforces that connector plugins
do not depend on `eventmesh-runtime`; the protocol plugins are the
runtime's *internal* extension point and live under
`eventmesh-protocol-plugin/`. Downstream consumers should not depend on
any of the protocol plugins directly - they should depend on the SDK
(see section 3).

## 3. Client SDKs

| SDK | Path | Status | Notes |
| --- | --- | --- | --- |
| Java (CloudEvents) | `eventmesh-sdks/eventmesh-sdk-java/client/cloudevents` | **GA** | The only modern SDK client. Recommended for all new code. |
| Java (gRPC) | `eventmesh-sdks/eventmesh-sdk-java/client/grpc` | **Beta** | For clients that need gRPC framing; backed by the gRPC resolver. |
| Java (TCP) | `eventmesh-sdks/eventmesh-sdk-java/client/tcp` | **Legacy** | Includes the `openmessage`, `cloudevent`, and `eventmeshmessage` impls. The OpenMessaging impl is being phased out (see migration below). |
| C | `eventmesh-sdks/eventmesh-sdk-c` | **Beta** | HTTP + CloudEvents. |
| Go | `eventmesh-sdks/eventmesh-sdk-go` | **Beta** | HTTP + CloudEvents. |
| Rust | `eventmesh-sdks/eventmesh-sdk-rust` | **Beta** | HTTP + CloudEvents. |

### 3.1 Java SDK public surface

Starting with this release, the Java SDK's public API surface - i.e. the
classes that downstream consumers should reference - is restricted to:

* `org.apache.eventmesh.client.cloudevents.CloudEventsClient` (and the
  `client/cloudevents/stream/*` types for streaming / SSE).
* `org.apache.eventmesh.client.grpc.*` (for clients that need gRPC).

Anything under `org.apache.eventmesh.client.tcp.*` is marked
**Legacy** and is excluded from the SDK's `api` configuration in
Gradle. The `io.openmessaging:openmessaging-api` dependency, which is
only used by the legacy TCP client's OpenMessaging implementation, is
demoted from `api` to `implementation` so that modern users (who only
depend on the CloudEvents client) do not see OMA types in their
classpath.

### 3.2 Migration from legacy TCP / OpenMessaging to CloudEvents HTTP

1. Switch the client to
   `org.apache.eventmesh.client.cloudevents.CloudEventsClient` (see
   `docs/feature/client-java.md`).
2. The CloudEvents wire format replaces the `EventMeshMessage` / OMA
   `Message` envelope. Event payload stays the same.
3. If you depended on the TCP framing for performance reasons, note that
   the CloudEvents HTTP path uses HTTP/1.1 keep-alive and SSE for
   streaming - comparable latency in the common case.
4. Legacy TCP support continues for at least one more minor release. A
   deprecation warning is logged on every legacy client construction;
   removal is planned for the next major version (see #5341 follow-up).

## 4. Architecture guardrails

`eventmesh-architecture-guard` enforces, via ArchUnit rules and the
`architecture-guard.yml` CI workflow, that:

* `eventmesh-protocol-plugin/*` modules are partitioned into
  `public` (SPI) and `internal` (implementation); the
  `public -> internal` direction is allowed, the reverse is not
  (see #5297).
* `eventmesh-connector-plugin/*` may not depend on `eventmesh-runtime`
  (see #5302 / #5297).
* The runtime's modern ingress path (`UniIngressService`,
  `UniHttpServer`) may not import legacy TCP / OMA wire types
  (verified by `git grep "import.*MeshMessage\\|import io.openmessaging"`
  in `eventmesh-runtime/src/main/` - only the legacy `tcp/` and
  `transport/http/LegacyHttp*` packages should match).

## 5. Acceptance

For the #5341 acceptance check, the following must hold:

* `docs/feature/protocols.md` exists and is linked from
  `docs/architecture/overview.md` (section 9) and from the README.
* The Java SDK's `build.gradle` declares `io.openmessaging:openmessaging-api`
  as `implementation`, not `api`.
* `git grep "import.*MeshMessage\\|import io.openmessaging"`
  in `eventmesh-runtime/src/main/` returns only files under
  `runtime/tcp/`, `runtime/transport/http/LegacyHttp*`, or
  `runtime/transport/http/EventMeshMessageHttpCodec` (the legacy HTTP
  codec).
* `eventmesh-architecture-guard` continues to pass on the develop branch
  after these changes.

## 6. References

* Parent issue: #5296 (Architecture Review, "New review questions" 2026-09-07)
* Tracking issue: #5341
* Architecture: `docs/architecture/overview.md`
* SDK guide: `docs/feature/client-java.md`
* Arch-guard: `eventmesh-architecture-guard/`, `docs/feature/architecture-guard.md`
* A2A wire: `docs/feature/a2a.md`
