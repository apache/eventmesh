# eventmesh-connector-http

## 1 HTTP Source Connector

### 1.1 Configuration

Before using HTTP source connector, you need to configure the server.
- Please configure `sourceEnable` to `true` in `/resource/server-config.yml` to enable source functionality.
- Please configure the source connector in `/resource/source-config.yml`, only the configuration under `connectorConfig` is described here:
  - `connectorName`, name of the connector.
  - (required) `path`, path of the API.
  - (required) `port`, port of the API.
  - `idleTimeout`, idle TCP connection timeout in seconds. A connection will timeout and be closed if no data is received nor sent within the `idleTimeout` seconds. The default is 0, which means don't timeout.

### 1.2 Startup

1. start eventmesh-runtime
2. start eventmesh-connector-http

When finished, the HTTP source connector will act as an HTTP server.

### 1.3 Sending messages
You can send messages to the source connector via HTTP.
```yaml
connectorConfig:
    connectorName: httpSource
    path: /test
    port: 3755
    idleTimeout: 5
```
The above example configures a URL `http://localhost:3755/test` in `source-config.yml`.

You can send messages in `binary` mode or `structured` mode as specified in [cloudevent-spec](https://github.com/cloudevents/spec/blob/v1.0.2/cloudevents/bindings/http-protocol-binding.md).

Here are two examples:

1. Sending a message in `binary` mode.
```shell
curl --location --request POST 'http://localhost:3755/test' \
--header 'ce-id: 1' \
--header 'ce-specversion: 1.0' \
--header 'ce-type: com.example.someevent' \
--header 'ce-source: /mycontext' \
--header 'ce-subject: test_topic' \
--header 'Content-Type: text/plain' \
--data-raw 'testdata'
```

2. Sending a message in `structured` mode.
```shell
curl --location --request POST 'http://localhost:3755/test' \
--header 'Content-Type: application/cloudevents+json' \
--data-raw '{
    "id": "1",
    "specversion": "1.0",
    "type": "com.example.someevent",
    "source": "/mycontext",
    "subject":"test_topic",
    "datacontenttype":"text/plain",
    "data": "testdata"
}'
```
