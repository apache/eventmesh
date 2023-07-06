# Connectors

## Connector 
A connector is a bridge that interacts with a specific external service or underlying data source (e.g., Databases) on behalf of user applications. A connector is either a Source or a Sink.

## Source
A source connector obtains data from an underlying data producer, and delivers it to targets after original data has been transformed into CloudEvents. It doesn't limit the way how a source retrieves data. (e.g., A source may pull data from a message queue or act as an HTTP server waiting for data sent to it).

## Sink 
A sink connector receives CloudEvents and does some specific business logics. (e.g., A MySQL Sink extracts useful data from CloudEvents and writes them to a MySQL database).
CloudEvents - A specification for describing event data in common formats to provide interoperability across services, platforms and systems.

## Implements
Add a new connector by implementing the source/sink interface using :

[eventmesh-openconnect-java](https://github.com/apache/eventmesh/tree/master/eventmesh-openconnect/eventmesh-openconnect-java)

## Connector Status

| Connector Name                        | Type   | Support Version |
|---------------------------------------|--------|-----------------|
| [RocketMQ](sink-connector-rocketmq)   | Sink   | N/A             |
| [RocketMQ](source-connector-rocketmq) | Source | N/A             |
| ChatGPT                               | Source | N/A             |
| ClickHouse                            | Sink   | N/A             |
| ClickHouse                            | Source | N/A             |
| DingTalk                              | Sink   | N/A             |
| Email                                 | Sink   | N/A             |
| FeiShu                                | Sink   | N/A             |
| Github                                | Source | N/A             |
| Http                                  | Sink   | N/A             |
| Http                                  | Source | N/A             |
| Jdbc                                  | Sink   | N/A             |
| Jdbc                                  | Source | N/A             |
| MySqlCDC                              | Source | N/A             |
| MongoDB                               | Sink   | N/A             |
| MongoDB                               | Source | N/A             |
| S3File                                | Sink   | N/A             |
| S3File                                | Source | N/A             |
| More connectors will be added...      | Source/Sink   | N/A             |       
