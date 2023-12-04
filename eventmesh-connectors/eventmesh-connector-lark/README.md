# eventmesh-connector-lark

## Lark Sink Server Config And Start

Before using eventmesh-connector-lark to sink events, you need to configure the server.
- Please customize `sinkEnable``=`true`/`false` in `/resource/server-config.yml` to turn on/off the sink function.
- Regarding `/resource/sink-config.yml`, only the configuration under `sinkConnectorConfig` is explained here:
    - `connectorName`, specify the connector name
    - (required) `appId`, the appId obtained from lark
    - (required) `appSecret`, the appSecret obtained from lark
    - `receiveIdType`, the type of receiving Id, the default and recommended use is `open_id`. Optional open_id/user_id/union_id/email/chat_id.
    - (Required) `receiveId`, receive Id, needs to correspond to `receiveIdType`.
    - `maxRetryTimes`, the maximum number of retransmissions when the sink event fails. The default is 3 times.
    - `retryDelayInMills`, when the sink event fails, the time interval for retransmitting the event. Default is 1s, unit is milliseconds.


## Sink CloudEvent To Lark

When using the eventmesh-connector-lark sinking event, you need to add the corresponding extension filed in CloudEvent:
- When key=`templatetype4lark`, value=`text`/`markdown`, indicating the text type of the event
- When the text type is markdown, you can add extension: key=`markdownmessagetitle4lark`, value indicates the title of the event.
- When key=`atusers4lark`, value=`id-0,name-0;id-1,name-1`, indicating that the event requires `@`certain users
    - It is recommended to use **open_id** for id.
    - When the text is of text type, the id can be **open_id/union_id/user_id**; when the text is of markdown type, the id can be **open_id/user_id**. In particular, when the application type is [custom robot](https://open.larksuite.com/document/ukTMukTMukTM/ucTM5YjL3ETO24yNxkjN) and the text is of markdown type, only the use of **open_id** to `@` the user is supported.
    - When the text is of text type and the id is invalid, name will be used instead for display; when the text is of markdown type and the id is invalid, an exception will be thrown directly (you should try to ensure the correctness of the id, and name can be considered omitted).
- When key=`atall4lark`, value=`true`/`false`, indicating that the event requires `@` everyone.


## Lark Open Platform API

For the Lark open platform API involved in this module, please click the following link:
- **Send Message**, please [view here](https://open.larksuite.com/document/server-docs/im-v1/message/create?appId=cli_a5e1bc31507ed00c)
- **text**, please [view here](https://open.larksuite.com/document/server-docs/im-v1/message-content-description/create_json#c9e08671)
- **markdown**, please [view here](https://open.larksuite.com/document/common-capabilities/message-card/message-cards-content/using-markdown-tags)

---

## Lark Sink Server的配置与启动

使用eventmesh-connector-lark下沉事件之前，需要进行server的配置。
- 请在`/resource/server-config.yml`中自定义`sinkEnable``=`true`/`false`以开启/关闭sink功能。
- 关于`/resource/sink-config.yml`，在此仅说明`sinkConnectorConfig`下的配置：
    - `connectorName`, 指定connector名称
    - (必需)`appId`, lark中获取的appId
    - (必需)`appSecret`, lark中获取的appSecret
    - `receiveIdType`， 接收Id的类型，默认且推荐使用`open_id`。可选open_id/user_id/union_id/email/chat_id。
    - (必需)`receiveId`, 接收Id，需要和`receiveIdType`对应。
    - `maxRetryTimes`, sink事件失败时，最大重传的次数。默认3次。
    - `retryDelayInMills`, sink事件失败时，重传事件的时间间隔。默认1s，单位为毫秒。

    
## 可下沉飞书的CLoudEvent

使用eventmesh-connector-lark下沉事件时，需要在CloudEvent中添加对应的extension filed：
- 当key=`templatetype4lark`时，value=`text`/`markdown`，表明该事件的文本类型
- 当文本类型为markdown时，可以添加extension：key=`markdownmessagetitle4lark`,value表明该事件的标题。
- 当key=`atusers4lark`时，value=`id-0,name-0;id-1,name-1`，表明该事件需要`@`某些用户
    - id推荐使用**open_id**。
    - 当文本属于text类型时，id可以是**open_id/union_id/user_id**;当文本属于markdown类型时，id可以是**open_id/user_id**。特别地，当应用类型为[自定义机器人](https://open.feishu.cn/document/ukTMukTMukTM/ucTM5YjL3ETO24yNxkjN)且文本属于markdown类型，则仅支持使用**open_id**来`@`用户。
    - 当文本属于text类型且id无效时，将利用name代替展示；当文本属于markdown类型时且id无效时，直接抛出异常(您应该尽量保证id的正确性，而name则可以考虑省略)。
- 当key=`atall4lark`时，value=`true`/`false`，表明该事件需要`@`所有人。


## 飞书开放平台API

有关该模块涉及到的飞书开放平台API，请点击以下链接：
- **发送消息**，请[查看这里](https://open.feishu.cn/document/server-docs/im-v1/message/create?appId=cli_a5e1bc31507ed00c)
- **text**，请[查看这里](https://open.feishu.cn/document/server-docs/im-v1/message-content-description/create_json#c9e08671)
- **markdown**，请[查看这里](https://open.feishu.cn/document/common-capabilities/message-card/message-cards-content/using-markdown-tags)
