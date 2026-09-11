# Redis connector

**Audience:** operators bridging EventMesh with Redis. Redis pub/sub bridge over Redisson. Source listens on a channel; sink publishes each CloudEvent to a channel.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.redis.source.RedisSourceConnector` | Redisson topic listener buffers channel messages; `poll()` drains them as CloudEvents. |
| Sink | `org.apache.eventmesh.connector.redis.sink.RedisSinkConnector` | Publishes each CloudEvent's payload to the Redis topic. |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.redisUrl` | `redis://localhost:6379` | Redis address |
| `connector.topic` | `source` | Channel to subscribe |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.redisUrl` | `redis://localhost:6379` | Redis address |
| `connector.topic` | `sink` | Channel to publish |

## Running

```bash
bin/start-connector.sh with -Dconnector.class=...RedisSourceConnector -Dconnector.mode=source -Dconnector.redisUrl=redis://redis:6379 -Dconnector.topic=events
```
