# Frame Protocol Conversion

**Audience:** contributors adding a protocol or understanding the internal
wire representation — the `EventMeshFrame` format, the `FrameAdaptor` SPI,
and end-to-end conversion chains per protocol. Part of the offset /
load-balancing / frame design trio; see also
[Offset management](offset-management.md).

---

## Architectural layering

```
┌─ external protocols (FrameAdaptor SPI) ─────────────────────────────┐
│                                                                      │
│  CloudEvents (HTTP/SSE/WS) → CloudEventsFrameAdaptor → EventMeshFrame │
│  MeshMessage (legacy TCP)  → MeshMessageFrameAdaptor  → EventMeshFrame │
│  A2A (JSON-RPC 2.0)        → A2AFrameAdaptor          → EventMeshFrame │
│  future protocol           → new FrameAdaptor         → EventMeshFrame │
│                                                                      │
│  The three protocols are peers; each converts directly to Frame —    │
│  none of them passes through CloudEvent.                             │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ external protocol → Frame, directly
┌──────────────────────────────▼───────────────────────────────────────┐
│  internal (runtime + storage): EventMeshFrame end to end              │
│                                                                        │
│  ingress publish → Frame → storage.send(Frame) → MQ bytes             │
│  MQ bytes → storage.poll() → Frame → dispatch/filter/TTL → Frame      │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ egress: Frame → the matching FrameAdaptor
┌──────────────────────────────▼───────────────────────────────────────┐
│  egress (FrameAdaptor SPI, per client connection protocol)            │
│                                                                        │
│  Frame → CloudEvents-JSON (SSE / WS / HTTP poll)                       │
│  Frame → MeshMessage Package (legacy TCP)                              │
│  Frame → A2A JSON-RPC bytes (A2A callbacks)                            │
└────────────────────────────────────────────────────────────────────────┘
```

**CloudEvent is not the internal representation** — it is one of the
external ingress formats for CloudEvents clients, converted to Frame by
`CloudEventsFrameAdaptor` on entry. MeshMessage and A2A likewise each have
their own independent adaptor; they never pass through CloudEvent.

## EventMeshFrame wire format

```
fixed header, 14 bytes:
  [magic:1=0xEF][ver:1=1][msgType:1][flags:1][seq:4][keyCount:2][dataLen:4]

KV attribute section (keyCount ×):
  [nameLen:2][name:UTF-8][valLen:4][value:UTF-8]

data:
  raw bytes (streaming chunk/prompt / event business payload)

msgType = STREAM_REQ(1) | STREAM_CHUNK(2) | EVENT(3)
flags   = bit0 done | bit1 hasError | bit2 hasMeta (streaming use)
```

## Field mapping per msgType

| msgType | Fixed-header fields | KV attributes | data |
| --- | --- | --- | --- |
| **STREAM_REQ** | — | `sid`=sessionId, `replyTo`=reply address, `model`?, `conv`? | prompt text |
| **STREAM_CHUNK** | `seq`=in-stream sequence, `flags.done`=termination mark | `sid`, `etype`?, `err`?, `meta`?(JSON) | chunk text |
| **EVENT** | — | `id`/`type`/`source`/`subject`/`time`/`emttl`/`emcorrelationid`/`empopck`/user extensions | event payload |

## The FrameAdaptor SPI

Bidirectional conversion between external protocols and `EventMeshFrame` is
defined by the `FrameAdaptor` SPI
(`eventmesh-protocol-plugin/eventmesh-protocol-api/.../FrameAdaptor.java`):

```java
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.PROTOCOL)
public interface FrameAdaptor {
    EventMeshFrame toFrame(ProtocolTransportObject proto);       // ingress
    ProtocolTransportObject fromFrame(EventMeshFrame frame);     // egress
    String getProtocolType();
}
```

The runtime never calls `EventMeshFrame.fromCloudEvent()` /
`.toCloudEvent()` / `MeshMessageFrameCodec` directly — everything goes
through `FrameAdaptors.get(<protocol name>)`. **Adding a new protocol is
just implementing `FrameAdaptor` + registering the SPI — no runtime code
changes.**

## Protocol plugin modules

| Module | FrameAdaptor | External protocol | Conversion |
| --- | --- | --- | --- |
| **protocol-api** | — (SPI interface + `FrameAdaptors` loader only) | — | zero implementations |
| **protocol-cloudevents** | `CloudEventsFrameAdaptor` | CloudEvents-JSON (HTTP/SSE/WS) | CE-JSON bytes ↔ Frame (field mapping via the CE object) |
| **protocol-meshmessage** | `MeshMessageFrameAdaptor` | MeshMessage Package (TCP) | Package ↔ Frame (direct field mapping, zero CE intermediary) |
| **protocol-a2a** | `A2AFrameAdaptor` | A2A JSON-RPC 2.0 | JSON-RPC bytes ↔ Frame (direct field mapping, zero CE intermediary) |
| ~~protocol-grpc~~ | — | — | **deleted** (empty shell, no sources) |

## The WireCodec SPI (internal MQ wire encoding)

Byte-level encode/decode for the internal MQ wire is defined by the
`WireCodec` SPI (`eventmesh-common/.../wire/`):

```java
public interface WireCodec {
    byte[] encode(StreamRequest request);
    StreamRequest decodeRequest(byte[] bytes);
    byte[] encode(StreamChunk chunk);
    StreamChunk decodeChunk(byte[] bytes);
    byte[] encode(CloudEvent event);
    CloudEvent decodeEvent(byte[] bytes);
}
```

- Default implementation: `EventMeshFrameCodec` (EventMeshFrame ↔ byte[]).
- Replaceable via `-Deventmesh.wire.codec=<fqcn>`.

## Full conversion chains per protocol

### CloudEvents (HTTP/SSE/WS clients)

**Ingress** (client → runtime):

```
SDK sends CloudEvents-JSON bytes
  → UniHttpServer.publish: body = CE-JSON bytes
  → UniIngressService.publish(topic, CloudEvent): CE → EventMeshFrame.fromCloudEvent(event)
  → storage.send(topic, frame): frame.encode() → MQ bytes
```

**Egress** (runtime → client):

```
storage.poll() → frame (EventMeshFrame)
  → SseConnection.send / WsConnection.send / UniHttpServer.poll
  → FrameAdaptors.toCloudEventsJson(frame): frame.toCloudEvent() → CE-JSON serialize
  → written as SSE data: / WS TextFrame / HTTP JSON
```

### MeshMessage (legacy TCP clients)

**Ingress**:

```
legacy TCP SDK sends Package (EventMeshMessage)
  → MeshMessagePackageRouter.route(pkg)
  → FrameAdaptors.get("meshmessage").toFrameSilent(pkg)
  → MeshMessageFrameAdaptor.toFrame:
      topic       → attributes["subject"]
      body        → data
      header.seq  → attributes["id"]
      properties  → attributes KV
  → TcpRequest.publish(topic, frame)
  → UniIngressService.publish(topic, frame)   (Frame overload — skips CE conversion)
  → storage.send(topic, frame)
```

**Egress**:

```
ReliableDispatcher.deliver → NettyTcpPushChannel.deliver(deliveryId, frame, callback)
  → FrameAdaptors.get("meshmessage").fromFrameSilent(frame)
  → MeshMessageFrameAdaptor.fromFrame:
      attributes["subject"] → topic
      data                  → body
      attributes KV         → properties
  → Package(header=ASYNC_MESSAGE_TO_CLIENT, body=EventMeshMessage)
  → channel.writeAndFlush(pkg)
```

### A2A (JSON-RPC clients)

**Ingress**:

```
A2A client sends JSON-RPC 2.0 bytes
  → FrameAdaptors.get("a2a").toFrameSilent(new ByteTransport(jsonBytes))
  → A2AFrameAdaptor.toFrame:
      parse JSON-RPC → extract method/params/_topic
      method        → attributes["ema2amethod"]
      params._topic → attributes["subject"]
      raw JSON      → data
  → EventMeshFrame
```

**Egress**:

```
EventMeshA2ATransport receives frame
  → frame.data() = the original A2A JSON-RPC bytes (stored verbatim at ingress)
  → ByteTransport(jsonBytes) → A2A client
```

## Frame conversion in the storage SPI

Storage plugins (`MeshStoragePlugin` + `LiteTopicCapable`) carry
EventMeshFrame directly on send/poll:

```
send(topic, EventMeshFrame frame):
  → rocketmq5: frame.encode() → MQ message body bytes
  → kafka:     frame.encode() → ProducerRecord value
  → rocketmq4: frame.encode() → MQ message body bytes

poll(topic, partition, ...):
  → MQ bytes → EventMeshFrame.decode(bytes)
  → with legacy CE-JSON fallback (old messages → EventMeshFrame.fromCloudEvent(deserialize))
```

## Where it landed

| Layer | Change | Status |
| --- | --- | --- |
| storage SPI | `MeshStoragePlugin.send/poll` + `LiteTopicCapable.sendLite/pullLite` all EventMeshFrame | ✅ |
| runtime dispatch | Delivery/BufferedEvent/PushChannel/Connection/DeadLetterSink/ReliableDispatcher/PushService + CloudEventFilter/SubscriptionManager fully Frame-typed | ✅ |
| streaming | Mode-1 (runtime↔agent cross-process) + Mode-2 (in-runtime pub/sub) all EventMeshFrame | ✅ |
| legacy connectors | Producer/Consumer SPI untouched (separate subsystem, speaks CE, boundary conversion) | ✅ kept |
| WireCodec SPI | default EventMeshFrameCodec, replaceable | ✅ |
| FrameAdaptor SPI | CloudEvents as an independent plugin + MeshMessage + A2A each with an independent adaptor | ✅ |
