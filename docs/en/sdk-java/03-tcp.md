# TCP Protocol

EventMesh SDK for Java implements the TCP producer and consumer of synchronous, asynchronous, and broadcast messages. Both the producer and consumer require an instance of `EventMeshTCPClientConfig` class that specifies the configuration of EventMesh TCP client. The `host` and `port` fields should match the `eventmesh.properties` file of EventMesh runtime.

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

## TCP Consumer

The consumer should implement the `ReceiveMsgHook` class, which is defined in [`ReceiveMsgHook.java`](https://github.com/apache/incubator-eventmesh/blob/master/eventmesh-sdk-java/src/main/java/org/apache/eventmesh/client/tcp/common/ReceiveMsgHook.java).

```java
public interface ReceiveMsgHook<ProtocolMessage> {
  Optional<ProtocolMessage> handle(ProtocolMessage msg);
}
```

The `EventMeshTCPClient` class implements the `subscribe` method. The `subscribe` method accepts the topic, the `SubscriptionMode`, and the `SubscriptionType`. The `handle` method will be invoked when the consumer receives a message from the topic it subscribes. If the `SubscriptionType` is `SYNC`, the return value of `handle` will be sent back to the producer.

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

## TCP Producer

### Asynchronous Producer

The `EventMeshTCPClient` class implements the `publish` method. The `publish` method accepts the message to be published and an optional timeout value and returns the response message from the consumer.

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

### Synchronous Producer

The `EventMeshTCPClient` class implements the `rr` method. The `rr` method accepts the message to be published and an optional timeout value and returns the response message from the consumer.

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
