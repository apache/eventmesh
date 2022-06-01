# Lightweight EventMesh SDK (CloudEvents)

## Introduction

[EventMesh(incubating)](https://github.com/apache/incubator-eventmesh) is a dynamic
cloud-native eventing infrastructure.

[CloudEvents](https://github.com/cloudevents/spec) is a specification for describing
event data in common formats to provide interoperability across services, platforms and systems.

As of May 2021, EventMesh contains the following
major components: `eventmesh-runtime`, `eventmesh-sdk-java` and `eventmesh-connector-rocketmq`.
For a customer to use EventMesh, `eventmesh-runtime` can be deployed as microservices to transmit
customer's events between event producers and consumers. Customer's applications can then interact
with `eventmesh-runtime` using `eventmesh-sdk-java` to publish/subscribe for events on given topics.

CloudEvents support has been a highly desired feature by EventMesh users. There are many reasons
for users to prefer using a SDK with CloudEvents support:
- CloudEvents is a more widely accepted and supported way to describe events. `eventmesh-sdk-java`
  currently uses the `LiteMessage` structure to describe events, which is less standardized.
- CloudEvents's Java SDK has a wider range of distribution methods. For example, EventMesh users
  currently need to use the SDK tarball or build from source for every EventMesh release. With
  CloudEvents support, it's easier for users to take a dependency on EventMesh's SDK using CloudEvents's
  public distributions (e.g. through a Maven configuration).
- CloudEvents's SDK supports multiple languages. Although EventMesh currently only supports a Java SDK,
  in future if more languages need to be supported, the extensions can be easier with experience on
  binding Java SDK with CloudEvents.

## Requirements

### Functional Requirements

| Requirement ID | Requirement Description | Comments |
| -------------- | ----------------------- | -------- |
| F-1            | EventMesh users should be able to depend on a public SDK to publish/subscribe events in CloudEvents format | Functionality |
| F-2            | EventMesh users should continue to have access to existing EventMesh client features (e.g. load balancing) with an SDK that supports CloudEvent | Feature Parity |
| F-3            | EventMesh developers should be able to sync `eventmesh-sdk-java` and an SDK with CloudEvents support without much effort/pain | Maintainability |

### Performance Requirements

| Requirement ID | Requirement Description | Comments |
| -------------- | ----------------------- | -------- |
| P-1            | Client side latency for SDK with CloudEvents support should be similar to current SDK | |

## Design Details

Binding with the CloudEvents Java SDK (similar to what Kafka already did, see Reference for more details)
should be an easy way to achieve the requirements.

Design details TBD.

## Appendix

### References
- https://cloudevents.github.io/sdk-java/kafka
