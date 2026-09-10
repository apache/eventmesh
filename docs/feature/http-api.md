# HTTP API Reference

**Audience:** application developers integrating with the EventMesh traffic
port directly (any language, no SDK required). Every endpoint below lives on
the **traffic HTTP port (default 8080)** and is registered in
`UniHttpServer`. The Java SDK wraps all of these — see the
[Java client guide](client-java.md).

---

## Conventions

- Events are [CloudEvents 1.0](https://cloudevents.io) JSON;
  `Content-Type: application/cloudevents+json` (structured mode) on writes.
- Success codes: `200` (data) / `202` (accepted, durably in the WAL).
- Error shape: `{"error": "<machine>", "message": "<human>"}` with the
  appropriate 4xx/5xx.
- If a `SecurityGate` is installed, every call passes it first:
  `401` unauthenticated, `403` forbidden, `429` quota exceeded — see
  [Security](../architecture/security.md).
- Oversized payloads are rejected `413` up front.

## Core pub/sub

| Endpoint | Method | Request | Response |
| --- | --- | --- | --- |
| `/events/publish?topic=X` | POST | CloudEvent JSON body | `202` |
| `/events/publish-batch?topic=X` | POST | JSON array of CloudEvents | `202` |
| `/events/subscribe` | POST | `{"clientId","topic","mode"}` | `200 {subscriptionId, instanceUrl}` |
| `/events/unsubscribe` | POST | `{"clientId","topic"?}` | `200 {removed: bool}` |
| `/events/poll?clientId=C&timeoutMs=N&max=M` | GET | — | `200 [{deliveryId, event}, …]` |
| `/events/ack` | POST | `{"deliveryId"}` | `200 {status:"acked"}` / `404` unknown |

`mode` ∈ `LOAD_BALANCE` | `BROADCAST` | `MULTICAST` (see
[Publish & subscribe](../feature/pubsub.md)). `instanceUrl`
is the load-balancer-pinned instance for subsequent polls (empty when no
advertised address is configured).

## Request-reply

| Endpoint | Method | Request | Response |
| --- | --- | --- | --- |
| `/events/request` | POST | CloudEvent (reply correlated on `emcorrelationid`) | `200` reply CloudEvent / `408` timeout |
| `/events/reply` | POST | CloudEvent with `emcorrelationid` extension | `202` |

## Streaming push

| Endpoint | Method | Notes |
| --- | --- | --- |
| `/events/stream?clientId=C` | GET (SSE) | `text/event-stream`; server pushes `data: <CloudEvent JSON>` frames |

WebSocket push uses a **separate port** (`-Deventmesh.ws.port`, WS upgrade)
— see [Streaming](../feature/streaming.md).

## Lite Topic (RocketMQ 5.x only)

| Endpoint | Method | Request |
| --- | --- | --- |
| `/events/lite/create` | POST | parent topic + lite queue definition |
| `/events/lite/publish` / `publish-bytes` | POST | event (JSON / raw bytes) to a lite queue |
| `/events/lite/poll` / `poll-bytes` | GET | clientId + lite queue |

See [Lite Topic](../feature/lite-topic.md).

## Agent control plane (v2 sessions)

| Endpoint | Method | Purpose |
| --- | --- | --- |
| `/agent/register` | POST | Register an agent |
| `/agent/ready` | POST | Flip readiness |
| `/agent/heartbeat` | POST | Keep-alive |
| `/agent/unregister` | POST | Deregister |
| `/session/open` / `/session/close` | POST | Open/close a streaming session |
| `/session/recommend` | POST | Ask the runtime which instance should own a session (sticky) |
| `/session/stream` | GET | Session SSE stream |
| `/session/publish` / `/session/subscribe` | POST | Mode-2 session pub/sub |

> The session layer is **not auto-wired** in the default bootstrap — an
> embedder enables it via builders (see
> [Deployment → deployment modes](deployment.md)).

## A2A gateway (separate port, Experimental)

`/a2a/tasks*` — task submission, status, SSE streaming. Runs on its own
listener (`A2AGatewayServer`), not the traffic port. See
[A2A protocol](../feature/a2a.md).

## Legacy bridge

| Endpoint | Purpose |
| --- | --- |
| `/eventmesh/publish`, `/eventmesh/subscribe`, `/eventmesh/unsubscribe` | Old-SDK compatibility (MeshMessage envelope); adapted onto the same frame path |

New integrations should use `/events/*` + CloudEvents — see
[Protocols & SDKs](protocols.md) for the migration path.
