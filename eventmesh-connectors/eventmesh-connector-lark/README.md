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
  - `sinkAsync`, whether to asynchronously sink events
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