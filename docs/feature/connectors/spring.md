# Spring connector

**Audience:** operators bridging EventMesh with Spring. Spring application bridge. Source buffers events published by Spring code (register a producer that feeds the buffer); sink exposes an injectable `EventForwarder` so the hosting Spring context receives EventMesh deliveries as native events.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.spring.source.SpringSourceConnector` | A `LinkedBlockingQueue` feeds `poll()`; host Spring code (e.g. an `@EventListener` adapter) enqueues CloudEvents. |
| Sink | `org.apache.eventmesh.connector.spring.sink.SpringSinkConnector` | Calls the injected `EventForwarder.forward(event)` per CloudEvent. **Fail-fast**: until the Spring context wires a forwarder via `setForwarder(...)`, `put()` throws so the runtime redelivers instead of silently dropping events. |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `(none)` | `—` | The source needs no external config |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `(none)` | `—` | Wire the forwarder programmatically from the Spring context |

## Running

```bash
Host inside a Spring Boot app; call SpringSinkConnector.setForwarder(event -> publisher.publishEvent(...)) after init
```
