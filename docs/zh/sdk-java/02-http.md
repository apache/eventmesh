# HTTP 协议

EventMesh Java SDK 实现了 HTTP 异步消息的生产者和消费者。二者都需要一个 `EventMeshHttpClientConfig` 类实例来指定 EventMesh HTTP 客户端的配置信息。其中的 `liteEventMeshAddr`、`userName` 和 `password` 字段需要和 EventMesh runtime `eventmesh.properties` 文件中的相匹配。

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

## HTTP 消费者

类 `EventMeshHttpConsumer` 实现了 `heartbeat`、`subscribe` 和 `unsubscribe` 方法。`subscribe` 方法接收一个 `SubscriptionItem` 对象的列表，其中定义了要订阅的话题和回调的 URL 地址。

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

EventMesh runtime 将发送一个包含 [CloudEvents 格式](https://github.com/cloudevents/spec) 信息的 POST 请求到这个回调的 URL 地址。类 [SubController.java](https://github.com/apache/incubator-eventmesh/blob/master/eventmesh-examples/src/main/java/org/apache/eventmesh/http/demo/sub/controller/SubController.java) 实现了 Spring Boot controller，它将接收并解析回调信息。

## HTTP 生产者

类 `EventMeshHttpProducer` 实现了 `publish` 方法。`publish` 方法接收将被发布的消息和一个可选的 timeout 值。消息应是下列类的一个实例：

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

## 使用Curl 命令

也可以不通过Event Mesh SDK来体验事件的收发功能

### 事件发送

```shell
curl -H "Content-Type:application/json" -X POST -d '{"name": "admin", "pass":"12345678"}' http://127.0.0.1:10105/eventmesh/publish/TEST-TOPIC-HTTP-ASYNC
```

启动eventmesh运行时服务后，可以使用curl命令将事件用HTTP post方法发布到指定的主题，并且Body必须是JSON格式。发布事件的url类似于(http://127.0.0.1:10105/eventmesh/publish/TEST-TOPIC-HTTP-ASYNC)，您将获得成功发布的结果。

### 事件订阅

```shell
curl -H "Content-Type:application/json" -X POST -d '{"url": "http://127.0.0.1:8088/sub/test", "consumerGroup":"TEST-GROUP", "topic":[{"mode":"CLUSTERING","topic":"TEST-TOPIC-HTTP-ASYNC","type":"ASYNC"}]}' http://127.0.0.1:10105/eventmesh/subscribe/local
```

启动eventmesh运行时服务器后，可以使用curl命令用HTTP post方法订阅指定的主题列表，并且Body必须是JSON格式。订阅url类似于(http://127.0.0.1:10105/eventmesh/subscribe/local)，您将获得订阅成功的结果。你应该注意Body中的`url`字段，这意味着你需要在指定的url上启动HTTP服务实现监听，你可以在`eventmesh-examples`模块中看到这个例子。