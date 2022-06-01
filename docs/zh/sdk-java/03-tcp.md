# TCP 协议

EventMesh Java SDK 实现了同步、异步和广播 TCP 消息的生产者和消费者。 二者都需要一个 `EventMeshHttpClientConfig` 类实例来指定 EventMesh TCP 客户端的配置信息。其中的 `host` 和 `port` 字段需要和 EventMesh runtime `eventmesh.properties` 文件中的相匹配。

```java
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import io.cloudevents.CloudEvent;

public class AsyncSubscribe implements ReceiveMsgHook<CloudEvent> {
  public static void main(String[] args) throws InterruptedException {
    EventMeshTCPClientConfig eventMeshTcpClientConfig = EventMeshTCPClientConfig.builder()
      .host(eventMeshIp)
      .port(eventMeshTcpPort)
      .userAgent(userAgent)
      .build();
    /* ... */
  }
}
```

## TCP 消费者

消费者应该实现 `ReceiveMsgHook` 类，其被定义在 [ReceiveMsgHook.java](https://github.com/apache/incubator-eventmesh/blob/master/eventmesh-sdk-java/src/main/java/org/apache/eventmesh/client/tcp/common/ReceiveMsgHook.java)。

```java
public interface ReceiveMsgHook<ProtocolMessage> {
  Optional<ProtocolMessage> handle(ProtocolMessage msg);
}
```

类 `EventMeshTCPClient` 实现了 `subscribe` 方法。该方法接收话题、`SubscriptionMode` 和 `SubscriptionType`。`handle` 方法将会在消费者从订阅的话题中收到消息时被调用。如果 `SubscriptionType` 是 `SYNC`，`handle` 的返回值将被发送回生产者。

```java
import org.apache.eventmesh.client.tcp.EventMeshTCPClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPClientFactory;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import io.cloudevents.CloudEvent;

public class TCPConsumer implements ReceiveMsgHook<CloudEvent> {
  public static TCPConsumer handler = new TCPConsumer();
  private static EventMeshTCPClient<CloudEvent> client;

  public static void main(String[] args) throws Exception {
    client = EventMeshTCPClientFactory.createEventMeshTCPClient(
      eventMeshTcpClientConfig,
      CloudEvent.class
    );
    client.init();

    client.subscribe(
      "eventmesh-sync-topic",
      SubscriptionMode.CLUSTERING,
      SubscriptionType.SYNC
    );

    client.registerSubBusiHandler(handler);
    client.listen();
  }

  @Override
  public Optional<CloudEvent> handle(CloudEvent message) {
    log.info("Messaged received: {}", message);
    return Optional.of(message);
  }
}
```

## TCP 生产者

### 异步生产者

类 `EventMeshTCPClient` 实现了 `public` 方法。该方法接收将被发布的消息和一个可选的 timeout 值，并返回来自消费者的响应消息。

```java
/* ... */
client = EventMeshTCPClientFactory.createEventMeshTCPClient(eventMeshTcpClientConfig, CloudEvent.class);
client.init();

CloudEvent event = CloudEventBuilder.v1()
  .withId(UUID.randomUUID().toString())
  .withSubject(ExampleConstants.EVENTMESH_GRPC_ASYNC_TEST_TOPIC)
  .withSource(URI.create("/"))
  .withDataContentType(ExampleConstants.CLOUDEVENT_CONTENT_TYPE)
  .withType(EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME)
  .withData(JsonUtils.serialize(content).getBytes(StandardCharsets.UTF_8))
  .withExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4 * 1000))
  .build();
client.publish(event, 1000);
```

### 同步生产者

类 `EventMeshTCPClient` 实现了 `rr` 方法。该方法接收将被发布的消息和一个可选的 timeout 值，并返回来自消费者的响应消息。

```java
/* ... */
client = EventMeshTCPClientFactory.createEventMeshTCPClient(eventMeshTcpClientConfig, CloudEvent.class);
client.init();

CloudEvent event = CloudEventBuilder.v1()
  .withId(UUID.randomUUID().toString())
  .withSubject(ExampleConstants.EVENTMESH_GRPC_ASYNC_TEST_TOPIC)
  .withSource(URI.create("/"))
  .withDataContentType(ExampleConstants.CLOUDEVENT_CONTENT_TYPE)
  .withType(EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME)
  .withData(JsonUtils.serialize(content).getBytes(StandardCharsets.UTF_8))
  .withExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4 * 1000))
  .build();

Package response = client.rr(event, 1000);
CloudEvent replyEvent = EventFormatProvider
  .getInstance()
  .resolveFormat(JsonFormat.CONTENT_TYPE)
  .deserialize(response.getBody().toString().getBytes(StandardCharsets.UTF_8));
```