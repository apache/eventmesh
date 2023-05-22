# Connectors

## Connector 
A connector is a bridge that interacts with a specific external service or underlying data source (e.g., Databases) on behalf of user applications. A connector is either a Source or a Sink.

## Source
A source connector obtains data from an underlying data producer and delivers it to targets, after original data has been transformed into CloudEvents. It doesn't limit the way how a source retrieves data. (e.g., A source may pull data from a message queue or act as an HTTP server waiting for data sent to it).

## Sink 
A sink connector receives CloudEvents and does some specific business logics. (e.g., A MySQL Sink extracts useful data from CloudEvents and writes them to a MySQL database).
CloudEvents - A specification for describing event data in common formats to provide interoperability across services, platforms and systems.

## Implements
Add a new connector by implementing the source/sink interface using :

[eventmesh-sdk-java](https://github.com/apache/eventmesh/tree/master/eventmesh-sdks/eventmesh-sdk-java)

[eventmesh-connector-api](https://github.com/apache/eventmesh/tree/master/eventmesh-connector-sdks/eventmesh-connector-sdks-java)

## Connector Release Status
EventMesh uses a grading system for connectors to help you understand what to expect from a connector:

|                      | Alpha                                                                                                                                                                                                            | Beta                                                                                                                                                                                                                                       | General Availability (GA)                                                                                                                                                                                      |
|----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Expectations         | An alpha connector signifies a connector under development and helps EventMesh gather early feedback and issues reported by early adopters. We strongly discourage using alpha releases for production use cases | A beta connector is considered stable and reliable with no backwards incompatible changes but has not been validated by a broader group of users. We expect to find and fix a few issues and bugs in the release before it’s ready for GA. | A generally available connector has been deemed ready for use in a production environment and is officially supported by EventMesh. Its documentation is considered sufficient to support widespread adoption. |
|                      |                                                                                                                                                                                                                  |                                                                                                                                                                                                                                            |                                                                                                                                                                                                                |
| Production Readiness | No                                                                                                                                                                                                               | Yes                                                                                                                                                                                                                                        | Yes                                                                                                                                                                                                            |

| Connector Name                        | Type   | Status | Support Version |
|---------------------------------------|--------|--------|-----------------|
| [RocketMQ](sink-connector-rocketmq)   | Sink   | Alpha  | N/A             |
| [RocketMQ](source-connector-rocketmq) | Source | Alpha  | N/A             |