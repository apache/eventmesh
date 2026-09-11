# File connector

**Audience:** operators bridging EventMesh with File. Local file bridge — the simplest way to try connectors. Source reads a text file line by line; sink appends each CloudEvent as a line to a file.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.file.source.FileSourceConnector` | `BufferedReader.readLine()` per poll (a small batch each tick), tracking the read position in memory. |
| Sink | `org.apache.eventmesh.connector.file.sink.FileSinkConnector` | Appends each CloudEvent (`id` + payload text) as a line via `PrintStream` (append mode). |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.filePath` | `/tmp/source.txt` | File to tail |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.filePath` | `/tmp/sink.txt` | File to append into |

## Running

```bash
bin/start-connector.sh with -Dconnector.class=...FileSourceConnector -Dconnector.mode=source -Dconnector.topic=lines -Dconnector.filePath=/var/log/app.log
```
