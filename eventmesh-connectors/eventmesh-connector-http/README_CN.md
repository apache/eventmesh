# HTTP

## 1 HTTP Source Connector

### 1.1 配置

使用 HTTP source connector 前，需要进行 server 的配置。
- 请在 `/resource/server-config.yml` 中配置 `sourceEnable`为`true` 以开启 source 功能。
- 请在 `/resource/source-config.yml`中配置 source connector, 在此仅说明 `connectorConfig` 下的配置：
  - `connectorName`, connector 的名称
  - （必需） `path`, 接口的路径
  - （必需） `port`, 接口的端口
  - `idleTimeout`, 空闲 TCP 连接超时时间，单位为秒。超过 `idleTimeout` 秒没有进行数据接收或发送的连接将会发生超时并被关闭。默认为 0, 不会发生超时。

### 1.2 启动

1. 启动 EventMesh Runtime
2. 启动 eventmesh-connector-http

完成后，HTTP source connector 会作为一个 HTTP 服务器对外提供服务。

### 1.3 发送消息

你可以通过 HTTP 向 source connector 发送消息。

```yaml
connectorConfig:
    connectorName: httpSource
    path: /test
    port: 3755
    idleTimeout: 5
```

上述的例子在`source-config.yml`中配置了一个 URL `http://localhost:3755/test`.

你可以按照 [cloudevent-spec](https://github.com/cloudevents/spec/blob/v1.0.2/cloudevents/bindings/http-protocol-binding.md) 中的规定，以`binary`模式或者`structured`模式发送消息。

这里是两个例子：

以`binary`模式发送消息。

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

以`structured`模式发送消息。

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