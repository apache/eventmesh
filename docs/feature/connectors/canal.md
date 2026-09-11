# Canal (MySQL CDC) connector

**Audience:** operators bridging EventMesh with Canal (MySQL CDC). MySQL change-data-capture via a deployed canal server. Source consumes binlog entries over the canal TCP protocol (batch ack only after EventMesh accepted the publish); sink replays row-change CloudEvents into a target MySQL as one all-or-nothing JDBC batch.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.canal.source.CanalSourceConnector` | `CanalConnector.getWithoutAck(batchSize)` each poll; ROWDATA entries become CloudEvents (`subject` = `logfile:offset` position). `commit()` acks the pending batch — at-least-once. |
| Sink | `org.apache.eventmesh.connector.canal.sink.CanalSinkConnector` | Executes each event's SQL (`subject`) in a single JDBC transaction; any failure rolls back and throws → no ACK → redelivery. |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.canalHost` | `localhost` | Canal server host |
| `connector.canalPort` | `11111` | Canal server port |
| `connector.destination` | `example` | Canal destination (instance) |
| `connector.username` | `` | Canal auth username |
| `connector.password` | `` | Canal auth password |
| `connector.subscribeFilter` | `.*\..*` | Binlog subscription filter (db.table regex) |
| `connector.batchSize` | `100` | Max entries per canal batch |
| `connector.pollTimeoutMs` | `1000` | Poll interval in ms |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.jdbcUrl` | `jdbc:mysql://localhost:3306/test` | Target MySQL JDBC URL |
| `connector.dbUser` | `root` | Database user |
| `connector.dbPassword` | `` | Database password |

## Running

```bash
bin/start-connector.sh with -Dconnector.class=...CanalSourceConnector -Dconnector.mode=source -Dconnector.canalHost=canal -Dconnector.destination=example -Dconnector.subscribeFilter=mydb\\..*
```
