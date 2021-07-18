# EventMesh Cloud Stream (Camel+Spring Cloud Stream)

## Introduction

[EventMesh(incubating)](https://github.com/apache/incubator-eventmesh) is a dynamic
cloud-native eventing infrastructure.

## An overview of Event Streaming
 
Event Streaming is an implementation of Pub/Sub architecture pattern,it consist of  

-Message or Event : Change of State.

-Topic : Partition in messaging middle ware broker.

-Consumer : Can subscribe to read events from broker topic.

-Producer : Generate events

Streaming of event is continuous flow of events in order to maintain order between events, events flow should be in a specific direction means from producers to consumers.

## Requirements

### Functional Requirements

| Requirement ID | Requirement Description | Comments |
| -------------- | ----------------------- | -------- |
| F-1            | EventMesh users should be able to achieve Event Streaming functionality in EventMesh | Functionality |
| F-2            | EventMesh users can apply dynamic user specific routing, filter, transformation logics etc | Functionality |

## Design Details

We are introduce EventMesh Cloud Stream component allow us to use programming model and binder abstractions
from Spring Cloud Stream natively within Apache Camel.

[Spring-Cloud-Stream](https://spring.io/projects/spring-cloud-stream) Spring Cloud Stream is a framework for building 
highly scalable event-driven microservices connected with shared messaging systems.

[Apache Camel](https://camel.apache.org/) Camel is an Open Source integration framework that empowers you to quickly 
and easily integrate various systems consuming or producing data.

## Architecture
![eventmesh-cloudstream-arch](../../images/eventmesh-cloudstream-arch.png?raw=true)

## Design

Design details TBD.

## Appendix

### References
- https://donovanmuller.blog/camel-spring-cloud-stream/
