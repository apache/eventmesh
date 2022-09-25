# Knative Connector插件

## 准备
### 创建Knative Source和Sink
我们使用 *cloudevents-player* [Knative服务](https://knative.dev/docs/serving/)作为例子。如果您不知道如何创建 *cloudevents-player* Knative服务作为source和sink，请按照这个[链接](https://knative.dev/docs/getting-started/first-source/#creating-your-first-source)的步骤进行创建。

### EventMesh配置文件
- 将以下配置加入 [eventmesh-starter/build.gradle](https://github.com/apache/incubator-eventmesh/blob/master/eventmesh-starter/build.gradle) 文件
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
- 将以下配置加入 [eventmesh-examples/build.gradle](https://github.com/apache/incubator-eventmesh/blob/master/eventmesh-examples/build.gradle)文件
```bash
plugins {
    id 'application'
}

application {
    mainClass = project.hasProperty("mainClass") ? project.getProperty("mainClass") : 'NULL'
}
```
- 在 [eventmesh-runtime/conf/eventmesh.properties](https://github.com/pchengma/incubator-eventmesh/blob/master/eventmesh-runtime/conf/eventmesh.properties) 文件中设置```eventMesh.connector.plugin.type=knative```变量

## 演示
### Knative发布事件消息/EventMesh订阅
#### 步骤1：启动一台EventMesh服务器
```bash
$ cd eventmesh-starter
$ ../gradlew -PmainClass=org.apache.eventmesh.starter.StartUp run
```

#### 步骤2：从Knative Source发布一条消息
```bash
$ curl -i http://cloudevents-player.default.127.0.0.1.sslip.io -H "Content-Type: application/json" -H "Ce-Id: 123456789" -H "Ce-Specversion: 1.0" -H "Ce-Type: some-type" -H "Ce-Source: command-line" -d '{"msg":"Hello CloudEvents!"}'
```

#### 步骤3：从EventMesh订阅
- 在 [ExampleConstants.java](https://github.com/apache/incubator-eventmesh/blob/master/eventmesh-examples/src/main/java/org/apache/eventmesh/common/ExampleConstants.java) 文件中设置 ```public static final String EVENTMESH_HTTP_ASYNC_TEST_TOPIC = "messages";```变量
```bash
$ cd eventmesh-examples
$ ../gradlew -PmainClass=org.apache.eventmesh.http.demo.sub.SpringBootDemoApplication run
```
#### 预期结果
以下```data```为```Hello CloudEvents!```的消息将会打印在EventMesh服务器的控制台上。
```bash
2022-09-05 16:37:58,237 INFO  [eventMesh-clientManage-] DefaultConsumer(DefaultConsumer.java:60) - \
[{"event":{"attributes":{"datacontenttype":"application/json","id":"123456789","mediaType":"application/json",\
"source":"command-line","specversion":"1.0","type":"some-type"},"data":{"msg":"Hello CloudEvents!"},"extensions":{}},\
"id":"123456789","receivedAt":"2022-09-05T10:37:49.537658+02:00[Europe/Madrid]","type":"RECEIVED"}]
```

### EventMessh发布事件消息/Knative订阅
#### 步骤1：启动一台EventMesh服务器
```bash
$ cd eventmesh-starter
$ ../gradlew -PmainClass=org.apache.eventmesh.starter.StartUp run
```

#### 步骤2：从EventMesh发布一条消息
我们用Knative Connector的测试程序来演示这个功能。
```bash
$ cd eventmesh-connector-plugin/eventmesh-connector-knative
$ ../../gradlew clean test --tests KnativeProducerImplTest.testPublish
```

#### 步骤3：从Knative订阅
```bash
$ curl http://cloudevents-player.default.127.0.0.1.sslip.io/messages
```

#### 预期结果
以下```data```为```Hello Knative from EventMesh!```的消息将会打印在EventMesh服务器的控制台上。
```bash
2022-09-05 16:52:41,633 INFO  [eventMesh-clientManage-] DefaultConsumer(DefaultConsumer.java:60) - \
[{"event":{"attributes":{"datacontenttype":"application/json","id":"1234","mediaType":"application/json",\
"source":"java-client","specversion":"1.0","type":"some-type"},"data":{"msg":["Hello Knative from EventMesh!"]},"extensions":{}},"id":"1234","receivedAt":"2022-09-05T10:52:32.999273+02:00[Europe/Madrid]","type":"RECEIVED"}]
```