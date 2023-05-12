Connector - A connector is a bridge that interacts with a specific external service or underlying data source (e.g., Databases) on behalf of user applications. A connector is either a Source or a Sink.

Source - A source connector obtains data from an underlying data producer and delivers it to targets, after original data has been transformed into CloudEvents. It doesn't limit the way how a source retrieves data. (e.g., A source may pull data from a message queue or act as an HTTP server waiting for data sent to it).

Sink - A sink connector receives CloudEvents and does some specific business logics. (e.g., A MySQL Sink extracts useful data from CloudEvents and writes them to a MySQL database).
CloudEvents - A specification for describing event data in common formats to provide interoperability across services, platforms and systems.

Add a new connector by implementting the source/sink interface using :

[eventmesh-sdk-java](https://github.com/apache/eventmesh/tree/master/eventmesh-sdk-java)

[eventmesh-connector-api](https://github.com/apache/eventmesh/tree/master/eventmesh-connectors/eventmesh-connector-api)
