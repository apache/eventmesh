# Deployment & Operations

**Audience:** operators deploying EventMesh — images and ports, single- vs
multi-instance coordination, connector deployment, security checklist, and
the incident runbook.

---

## Deployment modes

The capability surface is honest about what the default bootstrap starts
(issue #5380). Three supported modes:

| Mode | How to start | What it includes |
| --- | --- | --- |
| **Core Runtime** *(default)* | `bin/start.sh`, Docker image, `EventMeshApplication.main()` | Traffic HTTP (`/events/*`, legacy `/eventmesh/*`), admin plane (token-guarded, fail-closed), WebSocket push (opt-in `-Deventmesh.ws.port`), connector scheduling. **A2A gateway and v2 streaming sessions are NOT wired.** |
| **Session/streaming Runtime** | Embedder builds the session layer via builders (`withAgentRegistrar` / `withMatchmaker` / `withSessionRouter`) before `start()` — the channel strategy is an explicit embedder choice (no `-D`) | Everything in core, plus `/session/*` streaming endpoints |
| **A2A Gateway** | Separate launcher wiring `A2AGatewayServer` *(Experimental)* | `/a2a/tasks*` endpoints on their own port |

The A2A *quota classification*, *auth/ACL/quota gate* and *HTTP handler*
live in the core module and are exercised by tests, but the default
distribution does not start the A2A listener.

## Ports

| Port | Server | Notes |
| --- | --- | --- |
| 8080 | Traffic HTTP | `/events/*`, `/session/*`, `/agent/*`, legacy `/eventmesh/*` |
| 8081 | Admin HTTP | `/admin/*` + `/metrics` (Prometheus); token-guarded |
| 8082 | WebSocket *(opt-in)* | `-Deventmesh.ws.port=8082`; disabled by default |

## Running

### Docker

```shell
docker run -d --name eventmesh \
  -e EVENTMESH_STORAGE_TYPE=kafka \
  -e EVENTMESH_KAFKA_NAMESRV=YOUR_KAFKA:9092 \
  -p 8080:8080 -p 8081:8081 \
  apache/eventmesh:latest
```

`docker/Dockerfile` is a two-stage build (Gradle 8.5 + JDK 21 builder →
Temurin 21 JRE runtime, non-root). Storage/protocol plugins are discovered
from `./plugin/` by the SPI class loader.

### From source

```shell
git clone https://github.com/apache/eventmesh.git && cd eventmesh
export EVENTMESH_STORAGE_TYPE=kafka          # rocketmq | rocketmq5 | kafka
export EVENTMESH_KAFKA_NAMESRV=localhost:9092
./gradlew :eventmesh-runtime:clean :eventmesh-runtime:dist
cd eventmesh-runtime/dist && bash bin/start.sh
```

`bin/start.sh` maps `EVENTMESH_STORAGE_TYPE` / `EVENTMESH_HTTP_PORT` /
`EVENTMESH_ADMIN_PORT` / `EVENTMESH_OFFSET_PATH` (+ arbitrary `JAVA_OPTS`)
onto `-D` system properties.

### Verify

```shell
curl http://localhost:8081/admin/health     # {"status":"UP"}
```

## Single-instance vs multi-instance

The delivery topology (`-Deventmesh.delivery.topology`, see
[Control plane](../architecture/control-plane.md#1-deliverytopology)):

| Mode | Default | What it does | Requires |
| --- | --- | --- | --- |
| `LOCAL_STICKY_PULL` | **Yes** | Every instance polls every partition of every subscribed topic | Nothing — the single-instance default |
| `PARTITION_OWNED_PULL` | No | Each instance owns a strict subset of partitions (Meta CAS + fencing); no duplicate consumption | A meta store |

Multi-instance coordination keys:

```properties
-Deventmesh.meta.type=nacos          # cluster mode; currently: nacos
-Deventmesh.meta.addr=nacos:8848
-Deventmesh.instance.id=10.0.0.5:8080   # defaults to host:port
-Deventmesh.offset.meta=true         # opt-in remote offset tier (see below)
```

- An **unknown meta type fails fast at boot** in cluster mode (no silent
  in-memory isolation).
- The meta-backed offset tier is **opt-in** (`eventmesh.offset.meta=true`)
  — local RocksDB is the default deliver/ack progress layer because the
  rocketmq5 backend gets broker-side at-least-once via POP.

## Connectors

Connectors (23 source/sink plugins: Kafka, JDBC, MongoDB, S3, DingTalk,
Slack, WeChat, Lark, ChatGPT, MCP, Prometheus, …) run in a **separate
process** — `eventmesh-connector-runtime` — and talk to the runtime over
HTTP + CloudEvents:

```shell
docker run -e EVENTMESH_RUNTIME_URL=http://runtime:8080 eventmesh-connector:uni
```

Connector definitions are managed through `/admin/connectors` (CRUD) and
scheduled by the runtime's `ConnectorScheduler` with **generation
fencing** (#5382): every (re)assignment bumps a per-connector generation,
and a stale delayed start can never take over a connector running a newer
generation. Status: **Experimental** — only 4 of 23 plugins carry unit
tests today.

## Production checklist

- [ ] `EVENTMESH_STORAGE_TYPE` + backend address consistent on every instance
- [ ] `eventmesh.offset.path` on persistent storage (survives restarts)
- [ ] Decide WebSocket port (default disabled)
- [ ] **Set `-Deventmesh.admin.token`** — without it the admin API is
      fail-closed (503) by design
- [ ] Install a `SecurityGate` (auth tokens + ACL + quota + audit) for the
      traffic plane — see [Security](../architecture/security.md)
- [ ] TLS on the traffic port for anything beyond localhost
      (`eventmesh.tls.keystore.*`)
- [ ] Per-topic rate limits for known hot topics
- [ ] Prometheus scraping `/metrics`; alerts per
      [Observability](observability.md)

## Runbook — common failures

| Symptom | Check | Fix |
| --- | --- | --- |
| Boot: `no MeshStoragePlugin for '<type>'` | Storage type / plugin jar | Set correct `EVENTMESH_STORAGE_TYPE`; ensure the storage plugin jar is on the classpath |
| Boot: `topic not exist` (CODE 17) | Broker `autoCreateTopicEnable=false` | Pre-create the topic (4+ queues) |
| Boot: `unsupported eventmesh.meta.type` | Cluster mode meta type | Use `nacos` (or drop cluster mode) |
| Boot: `admin_locked` on admin calls | No admin token set | `-Deventmesh.admin.token=<secret>` |
| Events not delivered | `/admin/health` → `pendingDeliveries` | Subscriber not assigned / ACK backlog; check subscriptions on `/admin/subscriptions` |
| DLQ piling up | `/admin/dlq/browse?topic=` | Fix the consumer, then `/admin/dlq/replay` |
| Publishes throttled | `/admin/metrics` → `rateLimited` | Adjust `/admin/ratelimit` |
| Offset write failures growing | `RocksDBOffsetStore` counter | Disk full / permissions on `eventmesh.offset.path` |
| SSE client silent | Connection write failed → auto-nack | Client network; event will be re-delivered |

## Upgrade / rollback

- **Rolling restart**: stop → start per instance. Offsets persist (RocksDB)
  and in-flight deliveries re-dispatch on boot (at-least-once) — no lost
  window.
- **Rollback**: swap the previous image; the RocksDB offset/state format is
  unchanged across patch releases.

## Known limitations

- **Container-dependent test suites** (Testcontainers E2E, broker-failover
  chaos, rolling-upgrade drills) require a Docker environment; they are
  tracked as deferred coverage, not as passing guarantees
  ([HA plan](../architecture/review/production-ha-plan.md)).
- Nacos watch timing can fluctuate under churn; multi-instance watch-suite
  runs are occasionally flaky (timing, not correctness).
- A2A gateway and v2 streaming sessions need a dedicated launcher (see
  deployment modes above).
