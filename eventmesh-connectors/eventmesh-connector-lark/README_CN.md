# 飞书/Lark

## Lark Sink Server 的配置与启动

使用 eventmesh-connector-lark 下沉事件之前，需要进行 server 的配置。

- 请在`/resource/server-config.yml`中自定义`sinkEnable``=`true`/`false`以开启/关闭 sink 功能。
- 关于`/resource/sink-config.yml`，在此仅说明`sinkConnectorConfig`下的配置：
  - `connectorName`, 指定 connector 名称
  - (必需)`appId`, lark 中获取的 appId
  - (必需)`appSecret`, lark 中获取的 appSecret
  - `receiveIdType`，接收 Id 的类型，默认且推荐使用`open_id`。可选 open_id/user_id/union_id/email/chat_id。
  - (必需)`receiveId`, 接收 Id，需要和`receiveIdType`对应。
  - `sinkAsync`, 是否异步下沉事件
  - `maxRetryTimes`, sink 事件失败时，最大重传的次数。默认 3 次。
  - `retryDelayInMills`, sink 事件失败时，重传事件的时间间隔。默认 1s，单位为毫秒。


## 可下沉飞书的 CLoudEvent

使用 eventmesh-connector-lark 下沉事件时，需要在 CloudEvent 中添加对应的 extension filed：

- 当 key=`templatetype4lark`时，value=`text`/`markdown`，表明该事件的文本类型。
- 当文本类型为 markdown 时，可以添加 extension：key=`markdownmessagetitle4lark`,value 表明该事件的标题。
- 当 key=`atusers4lark`时，value=`id-0,name-0;id-1,name-1`，表明该事件需要`@`某些用户。
  - id 推荐使用**open_id**。
  - 当文本属于 text 类型时，id 可以是**open_id/union_id/user_id**;当文本属于 markdown 类型时，id 可以是**open_id/user_id**。特别地，当应用类型为[自定义机器人](https://open.feishu.cn/document/ukTMukTMukTM/ucTM5YjL3ETO24yNxkjN)且文本属于 markdown 类型，则仅支持使用**open_id**来`@`用户。
  - 当文本属于 text 类型且 id 无效时，将利用 name 代替展示；当文本属于 markdown 类型时且 id 无效时，直接抛出异常 (您应该尽量保证 id 的正确性，而 name 则可以考虑省略)。
- 当 key=`atall4lark`时，value=`true`/`false`，表明该事件需要`@`所有人。


## 飞书开放平台 API

有关该模块涉及到的飞书开放平台 API，请点击以下链接：

- **发送消息**，请[查看这里](https://open.feishu.cn/document/server-docs/im-v1/message/create?appId=cli_a5e1bc31507ed00c)
- **text**，请[查看这里](https://open.feishu.cn/document/server-docs/im-v1/message-content-description/create_json#c9e08671)
- **markdown**，请[查看这里](https://open.feishu.cn/document/common-capabilities/message-card/message-cards-content/using-markdown-tags)