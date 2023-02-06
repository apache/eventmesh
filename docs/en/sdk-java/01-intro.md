# Installation

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.eventmesh/eventmesh-sdk-java/badge.svg?style=for-the-badge)](https://maven-badges.herokuapp.com/maven-central/org.apache.eventmesh/eventmesh-sdk-java)

EventMesh SDK for Java is a collection of Java libraries to integrate EventMesh in a Java application. The SDK supports sending and receiving synchronous messages, asynchronous messages, and broadcast messages in TCP, HTTP, and gRPC protocols. The SDK implements EventMesh Message, CloudEvents, and OpenMessaging formats. The demo project is available in the [`eventmesh-example`](https://github.com/apache/incubator-eventmesh/tree/master/eventmesh-examples) module.

## Gradle

To install EventMesh SDK for Java with Gradle, declare `org.apache.eventmesh:eventmesh-sdk-java` as `implementation` in the dependencies block of the module's `build.gradle` file.

```groovy
dependencies {
  implementation 'org.apache.eventmesh:eventmesh-sdk-java:1.4.0'
}
```

## Maven

To install EventMesh SDK for Java with Maven, declare `org.apache.eventmesh:eventmesh-sdk-java` as a dependency in the dependencies block of the project's `pom.xml` file.

```xml
<dependencies>
  <dependency>
    <groupId>org.apache.eventmesh</groupId>
    <artifactId>eventmesh-sdk-java</artifactId>
    <version>1.4.0</version>
  </dependency>
</dependencies>
```
