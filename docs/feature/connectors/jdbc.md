# JDBC connector

**Audience:** operators bridging EventMesh with JDBC. Database bridge over plain JDBC. Source tails a table by a monotonically increasing id column; sink inserts each CloudEvent into a table via a parameterized INSERT.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.jdbc.source.JdbcSourceConnector` | Runs `connector.query` (with the last-seen id substituted for the `?`) each poll; each row (`id`, `data` columns) becomes a CloudEvent and advances the cursor. |
| Sink | `org.apache.eventmesh.connector.jdbc.sink.JdbcSinkConnector` | Executes `connector.insertSql` per CloudEvent (id = event id, data = payload bytes). |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.jdbcUrl` | `jdbc:mysql://localhost:3306/test` | JDBC URL |
| `connector.query` | `SELECT * FROM events WHERE id > ? ORDER BY id LIMIT 100` | Tail query (`?` = last id) |
| `connector.lastId` | `0` | Initial cursor |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.jdbcUrl` | `jdbc:mysql://localhost:3306/test` | JDBC URL |
| `connector.insertSql` | `INSERT INTO events (id, data) VALUES (?, ?)` | Insert statement |

## Running

```bash
bin/start-connector.sh with -Dconnector.class=...JdbcSourceConnector -Dconnector.mode=source -Dconnector.jdbcUrl=jdbc:mysql://db:3306/mydb
```
