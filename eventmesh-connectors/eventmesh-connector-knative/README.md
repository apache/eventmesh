# Knative Connector

## Prerequisite
### Create Knative Source and Sink
We use the *cloudevents-player* [Knative service](https://knative.dev/docs/serving/) as an example. If you do not know how to create *cloudevents-player* Knative service as source and sink, please follow the steps in this [link](https://knative.dev/docs/getting-started/first-source/#creating-your-first-source).


### Set up EventMesh Configuration
- Add the following lines to [eventmesh-starter/build.gradle](https://github.com/apache/eventmesh/blob/master/eventmesh-starter/build.gradle) file.
```
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
- Add the following lines to [eventmesh-examples/build.gradle](https://github.com/apache/eventmesh/blob/master/eventmesh-examples/build.gradle) file.
```
plugins {
    id 'application'
}

application {
    mainClass = project.hasProperty("mainClass") ? project.getProperty("mainClass") : 'NULL'
}
```
- Set `eventMesh.connector.plugin.type=knative` in [eventmesh-runtime/conf/eventmesh.properties](https://github.com/apache/eventmesh/blob/master/eventmesh-runtime/conf/eventmesh.properties) file.

## Demo
### Publish an Event Message from Knative and Subscribe from EventMesh
#### Step 1: Start an Eventmesh-Runtime Server
```bash
$ cd eventmesh-starter
$ ../gradlew -PmainClass=org.apache.eventmesh.starter.StartUp run
```

#### Step 2: Publish an Event Message from Knative
```bash
$ curl -i http://cloudevents-player.default.127.0.0.1.sslip.io -H "Content-Type: application/json" -H "Ce-Id: 123456789" -H "Ce-Specversion: 1.0" -H "Ce-Type: some-type" -H "Ce-Source: command-line" -d '{"msg":"Hello CloudEvents!"}'
```

#### Step 3: Subscribe from an EventMesh
- Set `public static final String EVENTMESH_HTTP_ASYNC_TEST_TOPIC = "messages";` in [ExampleConstants.java](https://github.com/apache/eventmesh/blob/master/eventmesh-examples/src/main/java/org/apache/eventmesh/common/ExampleConstants.java) file.
```bash
$ cd eventmesh-examples
$ ../gradlew -PmainClass=org.apache.eventmesh.http.demo.sub.SpringBootDemoApplication run
```

#### Expected Result
The following message with `data` field as `Hello CloudEvents!` will be printed on the console of EventMesh server.
```bash
2022-09-05 16:37:58,237 INFO  [eventMesh-clientManage-] DefaultConsumer(DefaultConsumer.java:60) - \
[{"event":{"attributes":{"datacontenttype":"application/json","id":"123456789","mediaType":"application/json",\
"source":"command-line","specversion":"1.0","type":"some-type"},"data":{"msg":"Hello CloudEvents!"},"extensions":{}},\
"id":"123456789","receivedAt":"2022-09-05T10:37:49.537658+02:00[Europe/Madrid]","type":"RECEIVED"}]
```

### Publish an Event Message from EventMesh and Subscribe from Knative
#### Step 1: Start an Eventmesh-Runtime Server
```bash
$ cd eventmesh-starter
$ ../gradlew -PmainClass=org.apache.eventmesh.starter.StartUp run
```

#### Step 2: Publish an Event Message from EventMesh
We use a test program to demo this function.
```bash
$ cd eventmesh-connector-plugin/eventmesh-connector-knative
$ ../../gradlew clean test --tests KnativeProducerImplTest.testPublish
```

#### Step 3: Subscribe from Knative
```bash
$ curl http://cloudevents-player.default.127.0.0.1.sslip.io/messages
```

#### Expected Result
The following message with `data` field as `Hello Knative from EventMesh!` will be printed on the console of EventMesh server.
```bash
2022-09-05 16:52:41,633 INFO  [eventMesh-clientManage-] DefaultConsumer(DefaultConsumer.java:60) - \
[{"event":{"attributes":{"datacontenttype":"application/json","id":"1234","mediaType":"application/json",\
"source":"java-client","specversion":"1.0","type":"some-type"},"data":{"msg":["Hello Knative from EventMesh!"]},"extensions":{}},"id":"1234","receivedAt":"2022-09-05T10:52:32.999273+02:00[Europe/Madrid]","type":"RECEIVED"}]
```