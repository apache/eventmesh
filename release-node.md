# 1.6.0 Release

## Feature

- [Feature #866](https://github.com/apache/incubator-eventmesh/pull/866) Support webhook
- [Feature #901](https://github.com/apache/incubator-eventmesh/pull/901) Support consul registry
- [Feature #1029](https://github.com/apache/incubator-eventmesh/pull/1029) Support etcd registry

## Enhancement

- [Enhancement #1005](https://github.com/apache/incubator-eventmesh/pull/1005) Remove some invalid imports
- [Enhancement #1015](https://github.com/apache/incubator-eventmesh/pull/1015) Made some fields are final

## Bug
- [Bug #980](https://github.com/apache/incubator-eventmesh/issues/980) The fields need to be made final
- [Bug #997](https://github.com/apache/incubator-eventmesh/issues/997) ProducerGroupConf#equals doesn't work
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

- [Document #1006](https://github.com/apache/incubator-eventmesh/issues/1006) Outdated instruction docs (CN)
- [Document #1033](https://github.com/apache/incubator-eventmesh/issues/1033) Update docs of quick-start
- [Document #1068](https://github.com/apache/incubator-eventmesh/issues/1068)  Translate the design document (cloudevents.md)
