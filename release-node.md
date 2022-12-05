# 1.7.0 Release

## Feature

- [Feature #1553](https://github.com/apache/incubator-eventmesh/issues/1553) Support rabbitmq connector
- [Feature #1261](https://github.com/apache/incubator-eventmesh/issues/1261) Support Pinpoint tracing plugin feature
- [Feature #1248](https://github.com/apache/incubator-eventmesh/issues/1248) Adds EventMesh Workflow Protocol
- [Feature #1247](https://github.com/apache/incubator-eventmesh/issues/1247) Adds EventMesh Catalog Protocol
- [Feature #1244](https://github.com/apache/incubator-eventmesh/issues/1244) Java SDK adds nacos naming selecotor
- [Feature #1092](https://github.com/apache/incubator-eventmesh/issues/1092) Java SDK adds EventMesh Catalog Client
- [Feature #1091](https://github.com/apache/incubator-eventmesh/issues/1091) Java SDK adds EventMesh Workflow Client
- [Feature #1090](https://github.com/apache/incubator-eventmesh/issues/1090) Support for managing EventMesh events using AsyncAPI
- [Feature #1040](https://github.com/apache/incubator-eventmesh/issues/1040) Support Pulsar connector plugin and Pulsar as event store
- [Feature #973](https://github.com/apache/incubator-eventmesh/issues/973) zookeeper registry
- [Feature #815](https://github.com/apache/incubator-eventmesh/issues/815) Support Rust
- [Feature #790](https://github.com/apache/incubator-eventmesh/issues/790) Support Knative as Eventing Infra
- [Feature #389](https://github.com/apache/incubator-eventmesh/issues/389) Support Redis
- [Feature #270](https://github.com/apache/incubator-eventmesh/issues/270) Support another optional storage engine Pravega

## Enhancement

- [Enhancement #2167](https://github.com/apache/incubator-eventmesh/issues/2167) Reliance on default encoding
- [Enhancement #2169](https://github.com/apache/incubator-eventmesh/issues/2169) Method checks the size of a collection against zero rather than using isEmpty() [WebhookTopicConfig]
- [Enhancement #2147](https://github.com/apache/incubator-eventmesh/issues/2147) add workflow run scripts
- [Enhancement #2069](https://github.com/apache/incubator-eventmesh/issues/2069) Support Go SDK Http EventMesh Message Protocol
- [Enhancement #2062](https://github.com/apache/incubator-eventmesh/issues/2062) Possible null pointer dereference due to return value of called method [SubScribeTask]
- [Enhancement #2056](https://github.com/apache/incubator-eventmesh/issues/2056) Use try-with-resources to manage resources [RejectClientByIpPortHandler]
- [Enhancement #2017](https://github.com/apache/incubator-eventmesh/issues/2017) Support Go SDK http protocol RR command
- [Enhancement #2016](https://github.com/apache/incubator-eventmesh/issues/2016) Possible null pointer dereference due to return value of called method [http SubController]
- [Enhancement #2008](https://github.com/apache/incubator-eventmesh/issues/2008) Support GO SDK Http protocol un-subscription
- [Enhancement #2004](https://github.com/apache/incubator-eventmesh/issues/2004) add workflow create command
- [Enhancement #2002](https://github.com/apache/incubator-eventmesh/issues/2002) Solve the project compile error
- [Enhancement #1999](https://github.com/apache/incubator-eventmesh/issues/1999) Refine rabbitmq connector unit test
- [Enhancement #1993](https://github.com/apache/incubator-eventmesh/issues/1993) Reliance on default encoding [WebhookFileListener]
- [Enhancement #1983](https://github.com/apache/incubator-eventmesh/issues/1983) Extract GO SDK protocol constant
- [Enhancement #1934](https://github.com/apache/incubator-eventmesh/issues/1934) upgrade workflow go.mod
- [Enhancement #1933](https://github.com/apache/incubator-eventmesh/issues/1933) optimize workflow-dal logic
- [Enhancement #1932](https://github.com/apache/incubator-eventmesh/issues/1932) optimize workflow-task logic
- [Enhancement #1931](https://github.com/apache/incubator-eventmesh/issues/1931) optimize workflow-jq logic
- [Enhancement #1929](https://github.com/apache/incubator-eventmesh/issues/1929) optimize workflow-catalog logic
- [Enhancement #1928](https://github.com/apache/incubator-eventmesh/issues/1928) upgrade workflow proto
- [Enhancement #1927](https://github.com/apache/incubator-eventmesh/issues/1927) add workflow examples demo
- [Enhancement #1822](https://github.com/apache/incubator-eventmesh/issues/1822) Support Go SDK producer message random sequence
- [Enhancement #1743](https://github.com/apache/incubator-eventmesh/issues/1743) Support Go SDK HTTP Client Load Balance
- [Enhancement #1682](https://github.com/apache/incubator-eventmesh/issues/1682 ) Java SDK add http connection pool
- [Enhancement #1670](https://github.com/apache/incubator-eventmesh/issues/1670) Add workflow mysql schema files
- [Enhancement #1648](https://github.com/apache/incubator-eventmesh/issues/1648) Modify worfklow scheduler config
- [Enhancement #1636](https://github.com/apache/incubator-eventmesh/issues/1636) Improve the performance of publish event in connector-pulsar
- [Enhancement #1628](https://github.com/apache/incubator-eventmesh/issues/1628) SSLContextFactory some config should configure in EventMeshHTTPConfiguration

## Bug

- [Bug #2163](https://github.com/apache/incubator-eventmesh/issues/2163) This method needlessly uses a String literal as a Charset encoding [SendSyncMessageProcessor]
- [Bug #2148](https://github.com/apache/incubator-eventmesh/issues/2148) The webhook test occurs with an NPE
- [Bug #1818](https://github.com/apache/incubator-eventmesh/issues/1818) Fix IOException in SSLContextFactory
- [Bug #1656](https://github.com/apache/incubator-eventmesh/issues/1656) The extension field of CloudEvent does not exist.
- [Bug #1654](https://github.com/apache/incubator-eventmesh/issues/1654) Occur NullPointerException when broadcastEventListener consumes message
- [Bug #1627](https://github.com/apache/incubator-eventmesh/issues/1627) ConsumerGroup subscribes multiple topics, only first topic can invoke url
- [Bug #1367](https://github.com/apache/incubator-eventmesh/issues/1367) Cannot find the webhook protocol adaptor
- [Bug #1350](https://github.com/apache/incubator-eventmesh/issues/1350) Fix WebHookProcessorTest test error
- [Bug #1347](https://github.com/apache/incubator-eventmesh/issues/1347) Pravega connector writer doesn't close when unsubscribing
- [Bug #1279](https://github.com/apache/incubator-eventmesh/issues/1279) gradle.properties incorrect under the eventmesh-connector-pulsar
- [Bug #1238](https://github.com/apache/incubator-eventmesh/issues/1238) Can't start the pulsar connector
- [Bug #1208](https://github.com/apache/incubator-eventmesh/issues/1208) Use zipkin hippen NullPointerException
- [Bug #1021](https://github.com/apache/incubator-eventmesh/issues/1021) Span is null when eventMeshServerTraceEnable is false
- [Bug #1022](https://github.com/apache/incubator-eventmesh/issues/1022) Two NPE problems with Tcp Protocol Resolver
- [Bug #1035](https://github.com/apache/incubator-eventmesh/issues/1035) Tcp UpStreamMsgContext retry infinite loop
- [Bug #1036](https://github.com/apache/incubator-eventmesh/issues/1036) The bug caused by the logical sequence of tcp closeSession
- [Bug #1038](https://github.com/apache/incubator-eventmesh/issues/1038) The result of validate target url method is opposite in http protocol
- [Bug #1052](https://github.com/apache/incubator-eventmesh/issues/1052) Only the first instance of the same consumer group receives the message in http protocol
- [Bug #1056](https://github.com/apache/incubator-eventmesh/issues/1056) Fix StringIndexOutOfBoundsException
- [Bug #1059](https://github.com/apache/incubator-eventmesh/issues/1059) NullPointException of Http Request
- [Bug #1064](https://github.com/apache/incubator-eventmesh/issues/1064) NullPointException Of ClientManageControllerTest
- [Bug #1074](https://github.com/apache/incubator-eventmesh/issues/1074) Fix PrometheusConfigurationTest running test fail


## Document and code style improvement

- [Document #2074](https://github.com/apache/incubator-eventmesh/issues/2074) Update Pravega connector doc
- [Document #2066](https://github.com/apache/incubator-eventmesh/issues/2066) Optimize http-demo zh document
- [Document #1520](https://github.com/apache/incubator-eventmesh/issues/1520) Update the eventmesh keywords.
- [Document #1500](https://github.com/apache/incubator-eventmesh/issues/1500) Fix the readme file.
- [Document #1496](https://github.com/apache/incubator-eventmesh/issues/1496) error words in 03-demo.md
- [Document #1368](https://github.com/apache/incubator-eventmesh/issues/1368) Knative Connector: Move Documentation to Design Directory
- [Document #1271](https://github.com/apache/incubator-eventmesh/issues/1271) Translation of documents [webhook.md]
- [Document #1246](https://github.com/apache/incubator-eventmesh/issues/1246) Pravega connector doc
- [Document #1213](https://github.com/apache/incubator-eventmesh/issues/1213) Support Knative as Eventing Infra: Documentation (Publish/Subscribe)
