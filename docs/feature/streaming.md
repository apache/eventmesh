# Streaming & Push Transports

**Audience:** application developers choosing how subscribers receive events
(pull vs push), and builders of LLM-style streaming workloads. Covers
long-poll, SSE, WebSocket, and the two streaming-session modes.

---

## Choosing a transport

| Transport | Endpoint | Direction | Port | Best for |
| --- | --- | --- | --- | --- |
| **HTTP long-poll** | `GET /events/poll` | client-driven | 8080 | Batch consumers, scheduled jobs, serverless; NAT-friendly |
| **SSE** | `GET /events/stream` | server push (one-way) | 8080 | Browser/mobile push, dashboards, LLM token streams |
| **WebSocket** | WS upgrade on the dedicated port | server push, bi-directional | 8082 (opt-in) | Low-latency interactive clients |

All three deliver identical CloudEvent payloads and share the same ACK /
retry / quota machinery — the only difference is the push direction.

## Long-poll

```shell
curl "http://localhost:8080/events/poll?clientId=order-svc&timeoutMs=30000"
# → [{ "deliveryId": "...", "event": { ...CloudEvent... } }, ...]
```

Batches up to `max` (default 100) buffered events; blocks up to
`timeoutMs`. The SDK wraps this in a background loop:
`client.subscribe("orders", "LOAD_BALANCE", handler)`.

## SSE

```shell
curl -N "http://localhost:8080/events/stream?clientId=order-svc" \
  -H "Accept: text/event-stream"
```

The runtime holds the response open and writes `data: <CloudEvent JSON>`
frames as events arrive. Write failures nack the dispatcher immediately
(the event is re-dispatched rather than lost to a dead connection). SDK:
`client.subscribeSse(topic, mode, handler)`.

## WebSocket

The WS server is a separate port (`-Deventmesh.ws.port=8082`, disabled by
default) because the upgrade handshake is a different protocol negotiation.
The **client must configure `wsUrl` explicitly** — pointing it at the HTTP
port fails the handshake:

```java
CloudEventsClient wsClient = CloudEventsClient.builder()
    .runtimeUrl("http://localhost:8080")   // publish / long-poll / SSE
    .wsUrl("http://localhost:8082")        // WS push
    .clientId("ws-sub").build();
wsClient.subscribeWs("orders", "BROADCAST", event -> { ... });
```

## LLM streaming (Mode 1 / Mode 2)

EventMesh provides two streaming patterns for LLM-style workloads — token
chunks flowing back, multi-turn conversation context — built on the session
layer (`eventmesh-runtime/.../session/`, `SessionRouter`).

| Mode | Use case | Direction | Entry |
| --- | --- | --- | --- |
| **Mode 1** — streaming call | client → agent (LLM), agent streams tokens back | request/response with push | `client.streaming().openSession(...)` |
| **Mode 2** — session pub/sub | producer writes chunks; consumers read via SSE | publish/subscribe | `client.subscribeSession(sessionId)` / `openSessionPublisher(sessionId)` |

### Mode 1 — single streaming call

```java
try (StreamingResponse r = client.streaming()
        .openSession(OpenSession.builder().clientId(client.clientId()).build())
        .call("Summarize this document…",)) {
    while (r.hasNext()) {
        System.out.print(r.next().text());
    }
}
```

### Mode 2 — session pub/sub

```java
try (SessionPublisher pub = client.openSessionPublisher(sessionId)) {
    pub.publish(chunkEvent);   // consumers on subscribeSession() receive via SSE
}
client.subscribeSession(sessionId, chunk -> render(chunk));
```

> The v2 streaming session layer is **not auto-wired** into the default
> bootstrap: an embedder builds it via the session builders
> (`withAgentRegistrar` / `withMatchmaker` / `withSessionRouter`) before
> `start()` — the channel strategy is an explicit embedder choice. See
> [Deployment → deployment modes](deployment.md#deployment-modes).

Load balancing for sessions uses the **sticky recommendation** model: the
runtime's `LoadMeter` self-reports load and `/session/recommend` pins a
client to an instance; instances never forward each other's session traffic.

## Where the code lives

| Piece | Location |
| --- | --- |
| SSE / WS connections | `eventmesh-runtime/.../push/SseConnection.java`, `WsConnection.java` |
| Push pump (nack on failure) | `eventmesh-runtime/.../push/ConnectionPushPump.java` |
| Long-poll channel | `eventmesh-runtime/.../push/LongPollingChannel.java` |
| WS server | `eventmesh-runtime/.../http/UniWsServer.java` |
| Session routing | `eventmesh-runtime/.../session/SessionRouter.java`, `Matchmaker.java` |
| Load meter | `eventmesh-runtime/.../ingress/LoadMeter.java` |
| SDK streaming | `eventmesh-sdks/.../cloudevents/stream/*` |
| Tests | `StreamingSdkE2ETest`, `LiteStreamCallIntegrationTest`, `WebSocketPushIntegrationTest`, `TlsIntegrationTest` |
