# Event Bridge

![event-bridge](/images/eventmesh-bridge.png)

Event Bridge 可以支持跨mesh集群的消息投递，下面展示这一功能的详细设计与体验步骤

![event-bridge-detail](/images/design-document/event-bridge-detail.png)

> 注：在本地体验这一功能时需要启动两台eventmesh实例，同时要修改`eventmesh-runtime`目录下的`eventmesh.properties`文件中的端口配置，避免端口冲突。便于下文描述，event-bridge特性按照上图信息进行表述。

## 01 远程订阅

**描述**：向cluster2 eventmesh发起远程订阅指令，cluster2 eventmesh收到指令后会携带订阅信息调用cluster1 eventmesh的本地订阅接口

**URL**: http://{cluster2 address}/eventmesh/subscribe/remote

**请求方式**：POST

**请求参数：**application/json 格式

| 参数名        | 类型   | 是否必填 | 说明                                                         |
| ------------- | ------ | -------- | ------------------------------------------------------------ |
| url           | String | 是       | 标识订阅url信息，暂时无用，后续可移除，目前仅为强校验，实际会被（/eventmesh/bridge/publish）替换 |
| consumerGroup | String | 是       | 标识消费组信息，实际会被cluster2的eventmesh配置信息替换      |
| topic         | List   | 是       | 标识订阅信息列表                                             |
| mode          | String | 是       | 标识消费模式，分为集群模式和广播模式                         |
| topic         | String | 是       | 标识订阅的topic                                              |
| type          | String | 是       | 标识消费类型，分为同步和异步                                 |
| remoteMesh    | String | 否       | 标识远程mesh地址，优先根据topic从注册中心获取，获取不到使用该字段替换 |

**请求样例：**

```json
{
  "url": "http://127.0.0.1:8088/sub/test",
  "consumerGroup": "TEST-GROUP",
  "topic": [
    {
      "mode": "CLUSTERING",
      "topic": "TEST-TOPIC-HTTP-ASYNC",
      "type": "ASYNC"
    }
  ],
  "remoteMesh" : "http://127.0.0.1:10105/eventmesh/subscribe/local"
}
```

## 02 本地订阅

**描述**：向cluster2的EventMesh实例发起本地订阅指令，cluster2的EventMesh收到订阅指令后会启动本地监听从event store收下来的消息，并推送给订阅信息中的url。

**URL**: http://{cluster2 address}/eventmesh/subscribe/local

**请求方式**：POST

**请求参数：**application/json 格式

| 参数名        | 类型   | 是否必填 | 说明                                 |
| ------------- | ------ | -------- | ------------------------------------ |
| url           | String | 是       | 标识订阅url信息                      |
| consumerGroup | String | 是       | 标识消费组信息                       |
| topic         | List   | 是       | 标识订阅信息列表                     |
| mode          | String | 是       | 标识消费模式，分为集群模式和广播模式 |
| topic         | String | 是       | 标识订阅的topic                      |
| type          | String | 是       | 标识消费类型，分为同步和异步         |

**请求样例：**

```JSON
{
  "url": "http://127.0.0.1:8088/sub/test",
  "consumerGroup": "TEST-GROUP",
  "topic": [
    {
      "mode": "CLUSTERING",
      "topic": "TEST-TOPIC-HTTP-ASYNC",
      "type": "ASYNC"
    }
  ]
}
```

## 03 发送消息

**描述**：向cluster1的EventMesh实例发送消息，cluster1的EventMesh收到消息后会发送到event store，再从event store收下来消息推送给cluster2的EventMesh url `/eventmesh/bridge/publish`。

**URL**: http://{cluster1 address}/eventmesh/publish/TEST-TOPIC-HTTP-ASYNC

**请求方式**：POST

**请求参数：**application/json 格式

**请求样例：**

```json
{
    "name":"test",
    "age":"19"
}
```

## 04远程去订阅

**描述**：向cluster2的EventMesh实例发送去除订阅指令，cluster2的EventMesh收到指令后会发送cluster1的EventMesh，cluster1的EventMesh会本地执行去除订阅

**URL**: http://{cluster2 address}/eventmesh/unsubscribe/remote

**请求方式**：POST

**请求参数：**application/json 格式

| 参数名        | 类型   | 是否必填 | 说明                                                         |
| ------------- | ------ | -------- | ------------------------------------------------------------ |
| url           | String | 是       | 标识要去除订阅url信息，暂时无用，后续可移除，目前仅为强校验，实际会被（/eventmesh/bridge/publish）替换 |
| consumerGroup | String | 是       | 标识要去除的消费组信息，实际会使用EventMesh cluster2的group信息替换 |
| topic         | List   | 是       | 标识订阅topic信息列表                                        |

**请求样例：**

```json
{
  "consumerGroup": "EventMeshTest-consumerGroup",
  "url": "http://127.0.0.1:8088/sub/test",
  "topic": [
    "TEST-TOPIC-HTTP-ASYNC"
  ]
}
```

## 05本地去订阅

**描述**：向cluster2的EventMesh实例发送去除订阅指令，cluster2的EventMesh收到指令后会本地执行去除订阅

**URL**: http://{cluster2 address}/eventmesh/unsubscribe/local

**请求方式**：POST

**请求参数：**application/json 格式

| 参数名        | 类型   | 是否必填 | 说明                   |
| ------------- | ------ | -------- | ---------------------- |
| url           | String | 是       | 标识要去除订阅url信息  |
| consumerGroup | String | 是       | 标识要去除的消费组信息 |
| topic         | List   | 是       | 标识订阅topic信息列表  |

**请求样例：**

```json
{
  "consumerGroup": "EventMeshTest-consumerGroup",
  "url": "http://127.0.0.1:8088/sub/test",
  "topic": [
    "TEST-TOPIC-HTTP-ASYNC"
  ]
}
```
