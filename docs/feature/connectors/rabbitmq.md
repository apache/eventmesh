# RabbitMQ connector

**Audience:** operators bridging EventMesh with RabbitMQ. Move events between EventMesh and a RabbitMQ broker over AMQP 0-9-1. Source consumes a queue; sink publishes to an exchange with an optional routing key.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.rabbitmq.source.RabbitmqSourceConnector` | `basicConsume` on the configured queue (auto-ack) into an internal buffer drained by `poll()`. |
| Sink | `org.apache.eventmesh.connector.rabbitmq.sink.RabbitmqSinkConnector` | `basicPublish` each CloudEvent's data to the exchange with the routing key. |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.host` | `localhost` | AMQP host |
| `connector.port` | `5672` | AMQP port |
| `connector.queue` | `source` | Queue to consume |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.host` | `localhost` | AMQP host |
| `connector.port` | `5672` | AMQP port |
| `connector.exchange` | `` | Target exchange (empty = default) |
| `connector.routingKey` | `` | Routing key |

## Running

```bash
bin/start-connector.sh with -Dconnector.class=...RabbitmqSinkConnector -Dconnector.mode=sink -Dconnector.clientId=c1 -Dconnector.exchange=amq.topic -Dconnector.routingKey=events
```
