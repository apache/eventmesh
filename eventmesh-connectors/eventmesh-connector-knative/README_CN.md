# Knative Connector 插件

## 准备

### 创建 Knative Source 和 Sink

我们使用 *cloudevents-player* [Knative 服务](https://knative.dev/docs/serving/)作为例子。如果您不知道如何创建 *cloudevents-player* Knative 服务作为 source 和 sink，请按照这个[链接](https://knative.dev/docs/getting-started/first-source/#creating-your-first-source)的步骤进行创建。

### EventMesh 配置文件

- 将以下配置加入 [eventmesh-starter/build.gradle](https://github.com/apache/eventmesh/blob/master/eventmesh-starter/build.gradle) 文件

```bash
plugins {
    id 'application'
}

application {
    mainClass = project.hasProperty("mainClass") ? project.getProperty("mainClass") : 'org.apache.eventmesh.starter.StartUp'
    applicationDefaultJvmArgs = [
            '-Dlog4j.configurationFile=../eventmesh-runtime/conf/log4j2.xml', '-Deventmesh.log.home=../eventmesh-runtime/logs', '-Deventmesh.home=../eventmesh-runtime', '-DconfPath=../eventmesh-runtime/conf'
    ]
}

dependencies {
    implementation project(":eventmesh-connector-plugin:eventmesh-connector-knative")
    implementation project(":eventmesh-runtime")
}
```

- 将以下配置加入 [eventmesh-examples/build.gradle](https://github.com/apache/eventmesh/blob/master/eventmesh-examples/build.gradle)文件

```bash
plugins {
    id 'application'
}

application {
    mainClass = project.hasProperty("mainClass") ? project.getProperty("mainClass") : 'NULL'
}
```

- 在 [eventmesh-runtime/conf/eventmesh.properties](https://github.com/apache/eventmesh/blob/master/eventmesh-runtime/conf/eventmesh.properties) 文件中设置`eventMesh.connector.plugin.type=knative`变量

## 演示

### Knative 发布事件消息 / EventMesh 订阅

#### 步骤 1：启动一台 EventMesh 服务器

```bash
$ cd eventmesh-starter
$ ../gradlew -PmainClass=org.apache.eventmesh.starter.StartUp run
```

#### 步骤 2：从 Knative Source 发布一条消息

```bash
$ curl -i http://cloudevents-player.default.127.0.0.1.sslip.io -H "Content-Type: application/json" -H "Ce-Id: 123456789" -H "Ce-Specversion: 1.0" -H "Ce-Type: some-type" -H "Ce-Source: command-line" -d '{"msg":"Hello CloudEvents!"}'
```

#### 步骤 3：从 EventMesh 订阅

- 在 [ExampleConstants.java](https://github.com/apache/eventmesh/blob/master/eventmesh-examples/src/main/java/org/apache/eventmesh/common/ExampleConstants.java) 文件中设置 `public static final String EVENTMESH_HTTP_ASYNC_TEST_TOPIC = "messages";`变量

```bash
$ cd eventmesh-examples
$ ../gradlew -PmainClass=org.apache.eventmesh.http.demo.sub.SpringBootDemoApplication run
```

#### 预期结果

以下`data`为`Hello CloudEvents!`的消息将会打印在 EventMesh 服务器的控制台上。

```bash
2022-09-05 16:37:58,237 INFO  [eventMesh-clientManage-] DefaultConsumer(DefaultConsumer.java:60) - \
[{"event":{"attributes":{"datacontenttype":"application/json","id":"123456789","mediaType":"application/json",\
"source":"command-line","specversion":"1.0","type":"some-type"},"data":{"msg":"Hello CloudEvents!"},"extensions":{}},\
"id":"123456789","receivedAt":"2022-09-05T10:37:49.537658+02:00[Europe/Madrid]","type":"RECEIVED"}]
```

### EventMesh 发布事件消息 / Knative 订阅

#### 步骤 1：启动一台 EventMesh 服务器

```bash
$ cd eventmesh-starter
$ ../gradlew -PmainClass=org.apache.eventmesh.starter.StartUp run
```

#### 步骤 2：从 EventMesh 发布一条消息

我们用 Knative Connector 的测试程序来演示这个功能。

```bash
$ cd eventmesh-connector-plugin/eventmesh-connector-knative
$ ../../gradlew clean test --tests KnativeProducerImplTest.testPublish
```

#### 步骤 3：从 Knative 订阅

```bash
$ curl http://cloudevents-player.default.127.0.0.1.sslip.io/messages
```

#### 预期结果

以下`data`为`Hello Knative from EventMesh!`的消息将会打印在 EventMesh 服务器的控制台上。

```bash
2022-09-05 16:52:41,633 INFO  [eventMesh-clientManage-] DefaultConsumer(DefaultConsumer.java:60) - \
[{"event":{"attributes":{"datacontenttype":"application/json","id":"1234","mediaType":"application/json",\
"source":"java-client","specversion":"1.0","type":"some-type"},"data":{"msg":["Hello Knative from EventMesh!"]},"extensions":{}},"id":"1234","receivedAt":"2022-09-05T10:52:32.999273+02:00[Europe/Madrid]","type":"RECEIVED"}]
```