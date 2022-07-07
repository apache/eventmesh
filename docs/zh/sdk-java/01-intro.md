# 安装

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.eventmesh/eventmesh-sdk-java/badge.svg?style=for-the-badge)](https://maven-badges.herokuapp.com/maven-central/org.apache.eventmesh/eventmesh-sdk-java)

EventMesh Java SDK 是在一个 Java 应用中集成 Eventmesh 所需的 Java 组件集合。SDK 支持使用 TCP、HTTP 和 gRPC 协议来发送和接收同步消息、异步消息和广播消息。SDK 实现了 EventMesh 消息、CloudEvents 和 OpenMessaging 形式。您可以在 [`eventmesh-example`](https://github.com/apache/incubator-eventmesh/tree/master/eventmesh-examples) 模块中查看示例项目。

## Gradle

使用 Gradle 安装 EventMesh Java SDK，您需要在模块的 `build.gradle` 文件的依赖块中将 `org.apache.eventmesh:eventmesh-sdk-java` 声明为 `implementation`。

```groovy
dependencies {
  implementation 'org.apache.eventmesh:eventmesh-sdk-java:1.4.0'
}
```

## Maven

使用 Maven 安装 EventMesh Java SDK，您需要在项目 `pom.xml` 文件的依赖块中声明 `org.apache.eventmesh:eventmesh-sdk-java`。

```xml
<dependencies>
  <dependency>
    <groupId>org.apache.eventmesh</groupId>
    <artifactId>eventmesh-sdk-java</artifactId>
    <version>1.4.0</version>
  </dependency>
</dependencies>
```