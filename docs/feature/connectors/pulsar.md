# Pulsar connector

**Audience:** operators bridging EventMesh with Pulsar. Bridge EventMesh topics with an Apache Pulsar cluster. Source subscribes to a topic; sink produces to a persistent topic.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.pulsar.source.PulsarSourceConnector` | Subscribes via `PulsarClient` consumer (bytes schema) and buffers messages for `poll()`. |
| Sink | `org.apache.eventmesh.connector.pulsar.sink.PulsarSinkConnector` | Produces each CloudEvent's data bytes via a Pulsar bytes producer. |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.serviceUrl` | `pulsar://localhost:6650` | Pulsar service URL |
| `connector.topic` | `persistent://public/default/source` | Topic to subscribe |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.serviceUrl` | `pulsar://localhost:6650` | Pulsar service URL |
| `connector.topic` | `persistent://public/default/sink` | Target topic |

## Event mapping

**Source** — each Pulsar message becomes one CloudEvent:

| CloudEvent attribute | Value |
| --- | --- |
| `id` | Pulsar message ID (`MessageId.toString()`) |
| `source` | `pulsar` |
| `type` | `pulsar.message` |
| `subject` | Pulsar topic name the message was read from |
| `datacontenttype` | `application/octet-stream` |
| `data` | Raw message payload (empty if the message has no payload) |

Pulsar message keys, properties and event time are not copied into the event.

**Sink** — only the CloudEvent `data` bytes are sent as the Pulsar message
payload (an empty payload if the event has no data). CloudEvent attributes and
extensions are not written as Pulsar message properties or keys.

## Precautions

- **`connector.topic` is shared with the connector runtime.** In single-connector
  mode the runtime also uses `-Dconnector.topic` as the EventMesh topic, so the
  Pulsar topic and the EventMesh topic have the same name. Pick a name that is
  valid for both, e.g. `persistent://public/default/orders`.
- **Numbered (`-Dconnector.N.*`) configuration is not read by this plugin.** The
  plugin only looks up the unnumbered keys `connector.serviceUrl` and
  `connector.topic`. Under the numbered form those keys are absent, so the
  plugin falls back to the defaults above. Run the Pulsar connector with the
  single-connector flags.
- **Fixed subscription.** The source always subscribes with the subscription
  name `connector-source` and the Pulsar client's default subscription type
  (Exclusive) and initial position (Latest). As a result:
  - a second source instance on the same topic fails to subscribe;
  - messages published before the subscription was first created are not read.
- **Source delivery is at-most-once.** Messages are acknowledged to Pulsar inside
  `poll()`, before EventMesh has accepted the publish, and `resume`/`commit`
  keep no offset. If the publish to EventMesh fails or the worker stops after
  `poll()`, those messages are not redelivered. This differs from the
  at-least-once model described in the [connector overview](README.md).
- **Sink failures are retried by EventMesh.** `producer.send` is synchronous; if it
  throws, `put` fails, the delivery stays un-ACKed and EventMesh redelivers it.
  Make downstream consumers idempotent.
- **No authentication or TLS options.** Only the service URL is configurable;
  clusters that require authentication tokens or TLS client settings are not
  supported by the current plugin.

## Running

```bash
CONNECTOR_OPTS="-Dconnector.class=org.apache.eventmesh.connector.pulsar.source.PulsarSourceConnector \
  -Dconnector.mode=source \
  -Dconnector.serviceUrl=pulsar://pulsar:6650 \
  -Dconnector.topic=persistent://public/default/my-topic" \
bin/start-connector.sh
```

For a sink, use
`-Dconnector.class=org.apache.eventmesh.connector.pulsar.sink.PulsarSinkConnector`
with `-Dconnector.mode=sink`. See [Running a connector](README.md#running-a-connector)
for the common runtime flags.

## References

- [Apache Pulsar documentation](https://pulsar.apache.org/docs/)
- [Pulsar Java client](https://pulsar.apache.org/docs/client-libraries-java/)
- [Pulsar topics, subscriptions and subscription types](https://pulsar.apache.org/docs/concepts-messaging/)
