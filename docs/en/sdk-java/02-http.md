# HTTP Protocol

EventMesh SDK for Java implements the HTTP producer and consumer of asynchronous messages. Both the producer and consumer require an instance of `EventMeshHttpClientConfig` class that specifies the configuration of EventMesh HTTP client. The `liteEventMeshAddr`, `userName`, and `password` fields should match the `eventmesh.properties` file of EventMesh runtime.

```java
import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;

public class HTTP {
  public static void main(String[] args) throws Exception {
    EventMeshHttpClientConfig eventMeshClientConfig = EventMeshHttpClientConfig.builder()
      .liteEventMeshAddr("localhost:10105")
      .producerGroup("TEST_PRODUCER_GROUP")
      .env("env")
      .idc("idc")
      .ip(IPUtils.getLocalAddress())
      .sys("1234")
      .pid(String.valueOf(ThreadUtils.getPID()))
      .userName("eventmesh")
      .password("password")
      .build();
      /* ... */
  }
}
```

## HTTP Consumer

The `EventMeshHttpConsumer` class implements the `heartbeat`, `subscribe`, and `unsubscribe` methods. The `subscribe` method accepts a list of `SubscriptionItem` that defines the topics to be subscribed and a callback URL.

```java
import org.apache.eventmesh.client.http.consumer.EventMeshHttpConsumer;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import com.google.common.collect.Lists;

public class HTTP {
  final String url = "http://localhost:8080/callback";
  final List<SubscriptionItem> topicList = Lists.newArrayList(
    new SubscriptionItem("eventmesh-async-topic", SubscriptionMode.CLUSTERING, SubscriptionType.ASYNC)
  );

  public static void main(String[] args) throws Exception {
    /* ... */
    eventMeshHttpConsumer = new EventMeshHttpConsumer(eventMeshClientConfig);
    eventMeshHttpConsumer.heartBeat(topicList, url);
    eventMeshHttpConsumer.subscribe(topicList, url);
    /* ... */
    eventMeshHttpConsumer.unsubscribe(topicList, url);
  }
}
```

The EventMesh runtime will send a POST request that contains the message in the [CloudEvents format](https://github.com/cloudevents/spec) to the callback URL. The [`SubController.java` file](https://github.com/apache/incubator-eventmesh/blob/master/eventmesh-examples/src/main/java/org/apache/eventmesh/http/demo/sub/controller/SubController.java) implements a Spring Boot controller that receives and parses the callback messages.

## HTTP Producer

The `EventMeshHttpProducer` class implements the `publish` method. The `publish` method accepts the message to be published and an optional timeout value. The message should be an instance of either of these classes:

- `org.apache.eventmesh.common.EventMeshMessage`
- `io.cloudevents.CloudEvent`
- `io.openmessaging.api.Message`

```java
import org.apache.eventmesh.client.http.producer.EventMeshHttpProducer;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.JsonUtils;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class HTTP {
  public static void main(String[] args) throws Exception {
    /* ... */
    EventMeshHttpProducer eventMeshHttpProducer = new EventMeshHttpProducer(eventMeshClientConfig);
    Map<String, String> content = new HashMap<>();
    content.put("content", "testAsyncMessage");

    CloudEvent event = CloudEventBuilder.v1()
      .withId(UUID.randomUUID().toString())
      .withSubject("eventmesh-async-topic")
      .withSource(URI.create("/"))
      .withDataContentType("application/cloudevents+json")
      .withType(EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME)
      .withData(JsonUtils.serialize(content).getBytes(StandardCharsets.UTF_8))
      .withExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4 * 1000))
      .build();
    eventMeshHttpProducer.publish(event);
  }
}
```

## Using Curl Command

You can also publish/subscribe event without eventmesh SDK.

### Publish

```shell
curl -H "Content-Type:application/json" -X POST -d '{"name": "admin", "pass":"12345678"}' http://127.0.0.1:10105/eventmesh/publish/TEST-TOPIC-HTTP-ASYNC
```

After you start the eventmesh runtime server, you can use the curl command publish the event to the specific topic with the HTTP POST method and the package body must be in JSON format. The publish url like (http://127.0.0.1:10105/eventmesh/publish/TEST-TOPIC-HTTP-ASYNC), and you will get the publish successful result.

### Subscribe

```shell
curl -H "Content-Type:application/json" -X POST -d '{"url": "http://127.0.0.1:8088/sub/test", "consumerGroup":"TEST-GROUP", "topic":[{"mode":"CLUSTERING","topic":"TEST-TOPIC-HTTP-ASYNC","type":"ASYNC"}]}' http://127.0.0.1:10105/eventmesh/subscribe/local
```

After you start the eventmesh runtime server, you can use the curl command to subscribe the specific topic list with the HTTP POST method, and the package body must be in JSON format. The subscribe url like (http://127.0.0.1:10105/eventmesh/subscribe/local), and you will get the subscribe successful result. You should pay attention to the `url` field in the package body, which means you need to set up an HTTP service at the specified URL, you can see the example in the `eventmesh-examples` module.

