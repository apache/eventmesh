# 1.5.0 Release

[Feature]

* Support golang sdk
* Add consumer, producer, heartbeat
* Add msg dispatcher on rece msg from mesh server
* Add go client api
* Add fake server for go sdk test
* Add go sdk example
* Support nacos registry
* Add WatchDirectoryTask and add file watch listener and manager
* Add ttl default value when building BatchMessage
* Http request improve support request uri for http processors
* Support http protocol adaptor
* Support event bridge pub/sub
* Add the eventmesh metadata to nacos
* Add trace buried point for EventmeshTcpServer in eventmesh-runtime


[BugFix]
* Could not find or load main class by test script
* Darwin operating system detection bug fix
* Fix LocalSubscribeEventProcessor json deserialize error
* Fix the key when removing item from ClientGroupMap


[Improvement]
* Remove tool directory @ruanwenjun
* Lint the documentations with markdownlint @liuxiaoyang
* Update bug_report.yml, fix typo
* Updated slack invite link in README
* Removed redundant code
* Optimized documentation docker script code blocks
* Upgrade spring libs & jackson-databind s version to fix CVEs issues
* Update ci.yml enable codecov
* Upgrade rocketmq libs version to fix CVEs
* Removed PropertyConst.java
* Remove some useless classes from eventmesh-common.
* Clean up some useless constants and classes.
* Add test code for module [eventmesh-trace-plugin]
* Add files via upload
* Transitively export the dependencies of [eventmesh-sdk-java] to other module
* Add the unit guidelines document and issue template
* Add the unit test document link and modify the unit test issue template
* Rewrite the English documentation
* Format the code in `eventmesh-sdk-java` and `eventmesh-common` (#874)
* Rewrite README.md and fix errors in the documentation
* Delete unnecessary shadow plugin
* Add unit test for sdk java
* Delete already defined java-library plugin and make lombok compileOnly
* Adapt unit tests to JDK 8
* Guide of EventMesh Java SDK
* Avoid setting the value of the field by reflection in the unit test.
* Add unit tests for EventMesh SDK for Java
* Remove unused methods in EventMesh-Connector-Api `Producer` interface
* Update Chinese version of README.md
* Add new contributor doc that have been reorganized
* Update em host in example
* Update licenserrc.yaml to ignore go.sum
* Add http request doc instruction
* Add remote processors for event bridge
* Code optimization
* Registry plugin is disabled by default
* Optimize trace module in eventmesh
* Check whether the registry is enabled

All pull request are [here](https://github.com/apache/incubator-eventmesh/pulls?q=is%3Apr+milestone%3A1.5.0+is%3Aclosed)