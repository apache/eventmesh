# MongoDB connector

**Audience:** operators bridging EventMesh with MongoDB. MongoDB bridge. Source tails a collection's insert stream; sink inserts each CloudEvent as a document.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.mongodb.source.MongodbSourceConnector` | Watches the collection (`watch()` cursor) and buffers change documents for `poll()`. |
| Sink | `org.apache.eventmesh.connector.mongodb.sink.MongodbSinkConnector` | Inserts a document per CloudEvent (`_id` = event id, `data` = payload). |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.mongoUri` | `mongodb://localhost:27017` | MongoDB connection URI |
| `connector.database` | `test` | Database |
| `connector.collection` | `events` | Collection to watch |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.mongoUri` | `mongodb://localhost:27017` | MongoDB connection URI |
| `connector.database` | `test` | Database |
| `connector.collection` | `sink` | Target collection |

## Running

```bash
bin/start-connector.sh with -Dconnector.class=...MongodbSourceConnector -Dconnector.mode=source -Dconnector.mongoUri=mongodb://mongo:27017 -Dconnector.database=events
```
