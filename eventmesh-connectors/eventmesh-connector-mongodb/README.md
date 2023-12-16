# MongoDB

## MongoDBSinkConnector: From eventmesh to MongoDB

1. launch your MongoDB server and eventmesh-runtime.
2. enable sinkConnector and check `sink-config.yml`.
3. send a message to eventmesh with the topic defined in `pubSubConfig.subject`

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

## MongoDBSourceConnector: From MongoDB to eventmesh

1. launch your MongoDB server and eventmesh-runtime.
2. enable sourceConnector and check `source-config.yml` (Basically the same as `sink-config.yml`)
3. start your `MongoDBSourceConnector` and you are ready to forward message.
4. write a CloudEvent message to `yourCol` at `yourDB` in your MongoDB and then you will receive the message in eventmesh.