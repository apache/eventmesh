# gRPC 协议

EventMesh Java SDK 实现了 gRPC 同步、异步和广播消息的生产者和消费者。二者都需要一个 `EventMeshHttpClientConfig` 类实例来指定 EventMesh gRPC 客户端的配置信息。其中的 `liteEventMeshAddr`、`userName` 和 `password` 字段需要和 EventMesh runtime `eventmesh.properties` 文件中的相匹配。

```java
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.consumer.ReceiveMsgHook;
import io.cloudevents.CloudEvent;

public class CloudEventsAsyncSubscribe implements ReceiveMsgHook<CloudEvent> {
  public static void main(String[] args) throws InterruptedException {
    EventMeshGrpcClientConfig eventMeshClientConfig = EventMeshGrpcClientConfig.builder()
      .serverAddr("localhost")
      .serverPort(10205)
      .consumerGroup(ExampleConstants.DEFAULT_EVENTMESH_TEST_CONSUMER_GROUP)
      .env("env").idc("idc")
      .sys("1234").build();
    /* ... */
  }
}
```

## gRPC 消费者

### 流消费者

EventMesh runtime 会将来自生产者的信息作为一系列事件流向流消费者发送。消费者应实现 `ReceiveHook` 类，其被定义在 [ReceiveMsgHook.java](https://github.com/apache/incubator-eventmesh/blob/master/eventmesh-sdk-java/src/main/java/org/apache/eventmesh/client/grpc/consumer/ReceiveMsgHook.java)。

```java
public interface ReceiveMsgHook<T> {
    Optional<T> handle(T msg) throws Throwable;
    String getProtocolType();
}
```

类 `EventMeshGrpcConsumer` 实现了 `registerListener`、`subscribe` 和 `unsubscribe` 方法。`subscribe` 方法接收一个 `SubscriptionItem` 对象的列表，其中定义了要订阅的话题。`registerListener` 接收一个实现了 `ReceiveMsgHook` 的实例。`handle` 方法将会在消费者收到订阅的主题消息时被调用。如果 `SubscriptionType` 是 `SYNC`，`handle` 的返回值将被发送回生产者。

```java
import org.apache.eventmesh.client.grpc.consumer.EventMeshGrpcConsumer;
import org.apache.eventmesh.client.grpc.consumer.ReceiveMsgHook;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import io.cloudevents.CloudEvent;

public class CloudEventsAsyncSubscribe implements ReceiveMsgHook<CloudEvent> {
    public static CloudEventsAsyncSubscribe handler = new CloudEventsAsyncSubscribe();
    public static void main(String[] args) throws InterruptedException {
        /* ... */
        SubscriptionItem subscriptionItem = new SubscriptionItem(
          "eventmesh-async-topic",
          SubscriptionMode.CLUSTERING,
          SubscriptionType.ASYNC
        );
        EventMeshGrpcConsumer eventMeshGrpcConsumer = new EventMeshGrpcConsumer(eventMeshClientConfig);

        eventMeshGrpcConsumer.init();
        eventMeshGrpcConsumer.registerListener(handler);
        eventMeshGrpcConsumer.subscribe(Collections.singletonList(subscriptionItem));
        /* ... */
        eventMeshGrpcConsumer.unsubscribe(Collections.singletonList(subscriptionItem));
    }

    @Override
    public Optional<CloudEvent> handle(CloudEvent message) {
      log.info("Messaged received: {}", message);
      return Optional.empty();
    }

    @Override
    public String getProtocolType() {
      return EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME;
    }
}
```

### Webhook 消费者

类 `EventMeshGrpcConsumer` 的 `subscribe` 方法接收一个 `SubscriptionItem` 对象的列表，其中定义了要订阅的主题和一个可选的 timeout 值。如果提供了回调 URL，EventMesh runtime 将向回调 URL 地址发送一个包含 [CloudEvents 格式](https://github.com/cloudevents/spec) 消息的 POST 请求。[SubController.java](https://github.com/apache/incubator-eventmesh/blob/master/eventmesh-examples/src/main/java/org/apache/eventmesh/grpc/sub/app/controller/SubController.java) 实现了一个接收并解析回调信息的 Spring Boot controller。

```java
import org.apache.eventmesh.client.grpc.consumer.EventMeshGrpcConsumer;
import org.apache.eventmesh.client.grpc.consumer.ReceiveMsgHook;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;

@Component
public class SubService implements InitializingBean {
  final String url = "http://localhost:8080/callback";

  public void afterPropertiesSet() throws Exception {
    /* ... */
    eventMeshGrpcConsumer = new EventMeshGrpcConsumer(eventMeshClientConfig);
    eventMeshGrpcConsumer.init();

    SubscriptionItem subscriptionItem = new SubscriptionItem(
      "eventmesh-async-topic",
      SubscriptionMode.CLUSTERING,
      SubscriptionType.ASYNC
    );

    eventMeshGrpcConsumer.subscribe(Collections.singletonList(subscriptionItem), url);
    /* ... */
    eventMeshGrpcConsumer.unsubscribe(Collections.singletonList(subscriptionItem), url);
  }
}
```

## gRPC 生产者

### 异步生产者

类 `EventMeshGrpcProducer` 实现了 `publish` 方法。`publish` 方法接收将被发布的消息和一个可选的 timeout 值。消息应是下列类的一个实例：

- `org.apache.eventmesh.common.EventMeshMessage`
- `io.cloudevents.CloudEvent`

```java
/* ... */
EventMeshGrpcProducer eventMeshGrpcProducer = new EventMeshGrpcProducer(eventMeshClientConfig);
eventMeshGrpcProducer.init();

Map<String, String> content = new HashMap<>();
content.put("content", "testAsyncMessage");

CloudEvent event = CloudEventBuilder.v1()
  .withId(UUID.randomUUID().toString())
  .withSubject(ExampleConstants.EVENTMESH_GRPC_ASYNC_TEST_TOPIC)
  .withSource(URI.create("/"))
  .withDataContentType(ExampleConstants.CLOUDEVENT_CONTENT_TYPE)
  .withType(EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME)
  .withData(JsonUtils.serialize(content).getBytes(StandardCharsets.UTF_8))
  .withExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4 * 1000))
  .build();
eventMeshGrpcProducer.publish(event);
```

### 同步生产者

类 `EventMeshGrpcProducer` 实现了 `requestReply` 方法。`requestReply` 方法接收将被发布的消息和一个可选的 timeout 值。方法会返回消费者返回的消息。消息应是下列类的一个实例：

- `org.apache.eventmesh.common.EventMeshMessage`
- `io.cloudevents.CloudEvent`

### 批量生产者

类 `EventMeshGrpcProducer` 重写了 `publish` 方法，该方法接收一个将被发布的消息列表和一个可选的 timeout 值。列表中的消息应是下列类的一个实例：

- `org.apache.eventmesh.common.EventMeshMessage`
- `io.cloudevents.CloudEvent`

```java
/* ... */
List<CloudEvent> cloudEventList = new ArrayList<>();
for (int i = 0; i < 5; i++) {
  CloudEvent event = CloudEventBuilder.v1()
    .withId(UUID.randomUUID().toString())
    .withSubject(ExampleConstants.EVENTMESH_GRPC_ASYNC_TEST_TOPIC)
    .withSource(URI.create("/"))
    .withDataContentType(ExampleConstants.CLOUDEVENT_CONTENT_TYPE)
    .withType(EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME)
    .withData(JsonUtils.serialize(content).getBytes(StandardCharsets.UTF_8))
    .withExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4 * 1000))
    .build();

  cloudEventList.add(event);
}

eventMeshGrpcProducer.publish(cloudEventList);
/* ... */
```