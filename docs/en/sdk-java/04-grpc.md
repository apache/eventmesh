# gRPC Protocol

EventMesh SDK for Java implements the gRPC producer and consumer of synchronous, asynchronous, and broadcast messages. Both the producer and consumer require an instance of `EventMeshGrpcClientConfig` class that specifies the configuration of EventMesh gRPC client. The `liteEventMeshAddr`, `userName`, and `password` fields should match the `eventmesh.properties` file of EventMesh runtime.

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

## gRPC Consumer

### Stream Consumer

The EventMesh runtime sends the message from producers to the stream consumer as a series of event streams. The consumer should implement the `ReceiveMsgHook` class, which is defined in [`ReceiveMsgHook.java`](https://github.com/apache/incubator-eventmesh/blob/master/eventmesh-sdk-java/src/main/java/org/apache/eventmesh/client/grpc/consumer/ReceiveMsgHook.java).

```java
public interface ReceiveMsgHook<T> {
    Optional<T> handle(T msg) throws Throwable;
    String getProtocolType();
}
```

The `EventMeshGrpcConsumer` class implements the `registerListener`, `subscribe`, and `unsubscribe` methods. The `subscribe` method accepts a list of `SubscriptionItem` that defines the topics to be subscribed to. The `registerListener` accepts an instance of a class that implements the `ReceiveMsgHook`. The `handle` method will be invoked when the consumer receives a message from the topic it subscribes. If the `SubscriptionType` is `SYNC`, the return value of `handle` will be sent back to the producer.

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

### Webhook Consumer

The `subscribe` method of the `EventMeshGrpcConsumer` class accepts a list of `SubscriptionItem` that defines the topics to be subscribed and an optional callback URL. If the callback URL is provided, the EventMesh runtime will send a POST request that contains the message in the [CloudEvents format](https://github.com/cloudevents/spec) to the callback URL. The [`SubController.java` file](https://github.com/apache/incubator-eventmesh/blob/master/eventmesh-examples/src/main/java/org/apache/eventmesh/grpc/sub/app/controller/SubController.java) implements a Spring Boot controller that receives and parses the callback messages.

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

## gRPC Producer

### Asynchronous Producer

The `EventMeshGrpcProducer` class implements the `publish` method. The `publish` method accepts the message to be published and an optional timeout value. The message should be an instance of either of these classes:

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

### Synchronous Producer

The `EventMeshGrpcProducer` class implements the `requestReply` method. The `requestReply` method accepts the message to be published and an optional timeout value. The method returns the message returned from the consumer. The message should be an instance of either of these classes:

- `org.apache.eventmesh.common.EventMeshMessage`
- `io.cloudevents.CloudEvent`

### Batch Producer

The `EventMeshGrpcProducer` class overloads the `publish` method, which accepts a list of messages to be published and an optional timeout value. The messages in the list should be an instance of either of these classes:

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
