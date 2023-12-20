# MongoDB

Connector runs as a standalone service by `main()` [eventmesh-connectors#connector](https://github.com/apache/eventmesh/tree/master/eventmesh-connectors#connector)

## MongoDBSinkConnector: From EventMesh to MongoDB

1. launch your MongoDB server and EventMesh Runtime.
2. enable sinkConnector and check `sink-config.yml`.
3. start your MongoDBConnectorServer, it will subscribe to the topic defined in `pubSubConfig.subject` of EventMesh Runtime and send data to `connectorConfig.collection` in your MongoDB.
4. send a message to EventMesh with the topic defined in `pubSubConfig.subject` and then you will receive the message in MongoDB.

```yaml
pubSubConfig:
  # default port 10000
  meshAddress: your.eventmesh.server:10000
  subject: TopicTest
  idc: FT
  env: PRD
  group: mongodbSink
  appId: 5031
  userName: mongodbSinkUser
  passWord: mongodbPassWord
connectorConfig:
  connectorName: mongodbSink
  # REPLICA_SET or STANDALONE is supported
  connectorType: STANDALONE
  # mongodb://root:root@127.0.0.1:27018,127.0.0.1:27019
  url: mongodb://127.0.0.1:27018
  database: yourDB
  collection: yourCol
```

## MongoDBSourceConnector: From MongoDB to EventMesh

1. launch your MongoDB server and EventMesh Runtime.
2. enable sourceConnector and check `source-config.yml` (Basically the same as `sink-config.yml`)
3. start your `MongoDBSourceConnector`, it will subscribe to the collection defined in `connectorConfig.collection` in your MongoDB and send data to `pubSubConfig.subject` of EventMesh Runtime.
4. write a CloudEvent message to `yourCol` at `yourDB` in your MongoDB and then you will receive the message in EventMesh.