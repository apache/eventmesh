# Security

**Audience:** operators and embedders securing an EventMesh deployment —
auth/ACL/quota/audit at the traffic plane, TLS on the wire, and the admin
plane's token guard.

---

## The unified SecurityGate

`SecurityGate` (issue #5304) is an **opt-in** per-request gate installed at
every ingress point. When installed, each request flows through one
immutable `RequestContext` and is checked in order:

```
   ingress request
        │
        ▼
   SecurityGate.check(RequestContext, EventMeshFrame)
        │
        ├─ 1. FilterChain.invoke(frame)          TokenAuthFilter → SignatureVerifierFilter → AclFilter
        │     verdict: ALLOW / DENY
        ├─ 2. if ALLOW → QuotaManager.acquire(ctx, Resource)
        │     Resources: CONNECTIONS | SUBSCRIPTIONS | THROUGHPUT | BACKLOG
        └─ 3. AuditSink.emit(decision, ctx, frame)   LoggingAuditSink (default) or custom
```

Any rejection short-circuits downstream — the request never touches the
storage SPI. Rejections map to HTTP **401** (unauthenticated), **403**
(forbidden), **429** (quota exceeded).

### Design points

- **One context per request.** `RequestContext` (builder-built, immutable)
  carries `tenantId`, `principal`, `roles`, `scopes`, `credential`,
  `remoteAddress`, `source`, `traceContext`, `quotaKey`, and an `Operation`
  enum (`PUBLISH` / `SUBSCRIBE` / `ACK` / `CONNECTOR` / `A2A` / `ADMIN`).
- **The gate composes policy, it does not duplicate it.** The existing
  `FilterChain` (`TokenAuthFilter`, `SignatureVerifierFilter`, `AclFilter`)
  still produces the auth/ACL verdict; new filters drop in without touching
  the gate.
- **Quota is keyed by `RequestContext.quotaKey()`** (defaults to `tenantId`)
  against a `Resource` enum — `TenantQuotaManager` is the in-memory
  implementation, `QuotaManager.unlimited()` disables enforcement.
- **Audit is best-effort by design**: an `AuditSink` failure is logged and
  must not take down the data path.
- **Fail-safe on malformed ACL entries**: logged at WARN, request denied,
  deny counted in gate metrics.

### Wiring points

| Ingress | Builder |
| --- | --- |
| Traffic HTTP (`/events/*`, SSE, WS upgrade) | `UniHttpServer#withSecurityGate(gate)` |
| A2A gateway | `A2AGatewayHttpHandler#withSecurityGate(gate)` |
| Connector scheduling | `ConnectorScheduler#withSecurityGate(gate)` (rejects with `ConnectorAccessDeniedException` → HTTP 403) |
| Embedder bootstrap | `EventMeshApplication#withSecurityGate(gate)` — installed into the traffic server at `start()` |

When no gate is installed the runtime behaves as before (open) — but new
endpoints are expected to install one, and embedders can fail closed at boot
before serving traffic.

## Rate limiting (per-topic)

`UniIngressService` enforces an optional per-topic token bucket
(`configureTopicRateLimit(topic, capacity, permitsPerSecond)`). Exhausted
buckets fail publish with `RateLimitedException` → HTTP **429**.
Cluster-wide limits are inspectable/adjustable at `/admin/ratelimit` —
see [Admin API](../feature/admin-api.md).

## TLS / mTLS on the traffic port

TLS terminates at the traffic HTTP server. Keys (read at boot by
`EventMeshApplication`):

| Key | Default | Purpose |
| --- | --- | --- |
| `eventmesh.tls.keystore` | — | Keystore path; empty = plain HTTP |
| `eventmesh.tls.keystore.password` | — | Keystore password |
| `eventmesh.tls.truststore` | — | Truststore for client certs (mTLS) |
| `eventmesh.tls.truststore.password` | — | Truststore password |
| `eventmesh.tls.protocol` | `TLSv1.3` | TLS protocol |
| `eventmesh.tls.needClientAuth` | `false` | `true` + truststore → require client certificates |

Embedders can instead call `withTls(SSLContext)` /
`withClientAuth(boolean)` before `start()`.

## Admin plane: fail-closed token guard (issue #5364)

Every admin endpoint **except `/admin/health`** is wrapped by a bearer-token
guard (`-Deventmesh.admin.token=<secret>`):

- Missing/wrong `Authorization: Bearer <token>` → **401**.
- **No token configured at all → 503 `admin_locked`** — the admin API is
  fail-closed by default; an operator who never set a token cannot use it
  accidentally. Only `/admin/health` (liveness probe) is exempt.
- The comparison is constant-time (`MessageDigest.isEqual`).

## Where the code lives

| Piece | Location |
| --- | --- |
| Gate core | `eventmesh-runtime/.../security/gate/SecurityGate.java`, `RequestContext.java`, `GateDecision.java` |
| Quota | `security/gate/QuotaManager.java`, `TenantQuotaManager.java`, `UnlimitedQuotaManager.java` |
| Audit | `security/gate/AuditSink.java`, `LoggingAuditSink.java`, `DisabledAuditSink.java` |
| Auth/ACL filters | `eventmesh-runtime/.../security/TokenAuthFilter.java`, `SignatureVerifierFilter.java`, `AclFilter.java` |
| TLS factory | `eventmesh-runtime/.../http/TlsContextFactory.java` |
| Admin guard | `eventmesh-runtime/.../admin/UniAdminServer.java` (`guarded()` / `authorized()`) |
| Tests | `SecurityGateTest` (per-Resource allow/deny, every Operation, short-circuit), `TlsIntegrationTest`, `RateLimitIntegrationTest` |
