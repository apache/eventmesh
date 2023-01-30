#事件网格工作流程

## 业务问题
假设您正在为电子商务商店构建一个简单的订单管理系统。 该系统应该能够接收和提供来自商店网站的新订单。 供应流程应该能够处理所有订单、处理付款以及处理发货。

为了实现高可用性和高性能，您使用事件驱动架构 (EDA) 构建系统，并构建微服务应用程序来处理商店前端、订单管理、支付处理和发货管理。 您将整个系统部署在云环境中。 为了处理高工作负载，您可以利用消息传递系统来缓冲负载，并扩展多个微服务实例。 该架构可能类似于：

![Workflow Use Case](../../images/design-document/workflow-use-case.jpg)

虽然每个微服务都在自己的事件通道上运行，但 EventMesh 在进行事件编排方面起着至关重要的作用。

We use [CNCF Serverless Workflow](https://serverlessworkflow.io/) to describe this Event Workflow Orchestration.

## CNCF 无服务器工作流

CNCF Serverless Workflow 定义了一个供应商中立、开源且完全由社区驱动的生态系统，用于定义和运行面向 Serverless 技术领域的基于 DSL 的工作流。

无服务器工作流定义了一种领域特定语言 (DSL) 来描述无状态和无状态基于工作流的无服务器功能和微服务编排。

More details could be found in its [official github site](https://github.com/serverlessworkflow/specification)

## 事件网格工作流程

我们利用无服务器工作流 DSL 来描述 EventMesh 工作流。 根据其规范，工作流由一系列用于描述控制流逻辑的工作流状态组成。 目前我们只支持与事件相关的工作流状态。 请参阅 [工作流 DSL 设计](#workflow-dsl-design-wip) 中的支持状态。

“工作流状态”可以包括适用的“操作”，或应在工作流执行期间调用的服务/功能。 这些“动作”可以引用可重用的“功能”定义，这些定义定义了这些功能/服务应该如何被调用。 它们还可以引用触发基于事件的服务调用的事件，以及等待表示此类基于事件的服务调用完成的事件。
In EDA solution, we usually defined our event-driven microservice using AsyncAPI. Serverless workflow `function` definitions support defining invocation semantics using AsyncAPI. See [Using Funtions for AsyncAPI Service](https://github.com/serverlessworkflow/specification/blob/main/specification.md#using-functions-for-async-api-service-invocations) for more information.

### 异步API

AsyncAPI 是一项开源计划，旨在改善事件驱动架构 (EDA) 的当前状态。
我们的长期目标是让使用 EDA 就像使用 REST API 一样简单。
从文档到代码生成，从发现到事件管理。
您现在应用于 REST API 的大多数流程也适用于您的事件驱动/异步 API。

See AsyncAPI detail in the [official site](https://www.asyncapi.com/docs/getting-started)

### 工作流程示例

在这个例子中，我们构建了上面订单管理系统的事件驱动工作流。

首先，我们需要为我们的微服务应用程序定义 AsyncAPI 定义。

- 在线商店应用

```yaml
异步API：2.2.0
信息：
   标题：在线商店应用程序
   版本：'0.1.0'
渠道：
   商店/订单：
     订阅：
       operationId：newStoreOrder
       信息：
         $ref : '#/组件/新订单'

```

- 订购服务

```yaml
异步API：2.2.0
信息：
   title: 订单服务
   版本：'0.1.0'
渠道：
   订单/入库：
     发布：
       operationId：发送订单
       信息：
         $ref : '#/组件/订单'
   订单/出站：
     订阅：
       operationId: processedOrder
       信息：
         $ref : '#/组件/订单'
```

- 支付服务

```yaml
异步API：2.2.0
信息：
   title: 支付服务
   版本：'0.1.0'
渠道：
   付款/入站：
     发布：
       operationId：发送付款
       信息：
         $ref : '#/组件/OrderPayment'
   支付/出站：
     订阅：
       operationId：付款收据
       信息：
         $ref : '#/组件/OrderPayment'
```

- 寄件服务

```yaml
异步API：2.2.0
信息：
   title: 运输服务
   版本：'0.1.0'
渠道：
   装运/入境：
     发布：
       operationId：sendShipment
       信息：
         $ref : '#/组件/OrderShipment'
```

定义完成后，我们将定义描述订单管理业务逻辑的订单工作流。

```yaml
id: storeorderworkflow
版本：'1.0'
规格版本：'0.8'
名称：商店订单管理工作流
状态：
   - 名称：接收新订单事件
     类型：事件
     关于事件：
       - 事件参考：
           - 新订单事件
         动作：
           - 事件参考：
               triggerEventRef：OrderServiceSendEvent
               resultEventRef：OrderServiceResultEvent
           - 事件参考：
               triggerEventRef：PaymentServiceSendEvent
               resultEventRef：PaymentServiceResultEvent
     过渡：检查付款状态
   - 名称：检查付款状态
     类型：开关
     数据条件：
       - 姓名：支付成功
         条件：“${.payment.status =='成功'}”
         过渡：发送订单发货
       - 名称：付款被拒绝
         条件：“${.payment.status =='拒绝'}”
         结束：真
     默认条件：
       结束：真
   - 名称：发送订单发货
     类型：操作
     动作：
       - 事件参考：
           triggerEventRef：ShipmentServiceSendEvent
     结束：真
事件：
   - 名称：NewOrderEvent
     来源：文件：//onlineStoreApp.yaml#newStoreOrder
     类型：asyncapi
     种类：消费
   - 名称：OrderServiceSendEvent
     来源：文件：//orderService.yaml#sendOrder
     类型：asyncapi
     种类：生产
   - 名称：OrderServiceResultEvent
     来源：文件：//orderService.yaml#processedOrder
     类型：asyncapi
     种类：消费
   - 名称：PaymentServiceSendEvent
     来源：文件：//paymentService.yaml#sendPayment
     类型：asyncapi
     种类：生产
   - 名称：PaymentServiceResultEvent
     来源：文件：//paymentService.yaml#paymentReceipt
     类型：asyncapi
     种类：消费
   - 名称：ShipmentServiceSendEvent
     来源：文件：//shipmentService.yaml#sendShipment
     类型：asyncapi
     种类：生产
```

对应的工作流程图如下：

![Workflow Diagram](../../images/design-document/workflow-diagram.png)

## EventMesh 工作流引擎

在下面的架构图中，EventMesh Catalog、EventMesh Workflow Engine 和 EventMesh Runtime 运行在三个不同的处理器中。

![Workflow Architecture](../../images/design-document/workflow-architecture.jpg)

运行工作流的步骤如下：

1. 在环境中部署发布者和订阅者应用程序。
    使用 AsyncAPI 描述 App API，生成 asyncAPI yaml。
    使用 AsyncAPI 在 EventMesh 目录中注册发布者和订阅者应用程序。

2. 在 EventMesh Workflow Engine 中注册 Serverless Workflow DSL。

3. EventMesh Workflow Engine查询EventMesh Catalog for Publisher和Subscribers在Workflow DSL`function`中需要

4. 事件驱动的应用程序是将事件发布到 EventMesh Runtime 以触发工作流。 EventMesh Workflow Engine 还发布和订阅事件以编排事件。

### EventMesh目录设计

EventMesh Catalog 存储发布者、订阅者和频道元数据。 由以下模块组成：

- 异步 API 解析器

使用AsyncAPI社区提供的SDK（参见[工具列表](https://www.asyncapi.com/docs/community/tooling)），
   解析并验证 AsyncAPI yaml 输入，并生成 AsyncAPI 定义。
- 发布者、频道、订阅者模块

   从 AsyncAPI 定义存储发布者、订阅者和频道信息。

### EventMesh 工作流引擎设计

EventMesh 工作流引擎由以下模块组成：

- 工作流解析器

   使用 Serverless Workflow 社区提供的 SDK（参见支持的 [SDKs](https://github.com/serverlessworkflow/specification#sdks)），
   解析和验证工作流 DSL 输入，并生成工作流定义。

- 工作流模块

   它管理一个工作流实例生命周期，从创建、开始、停止到销毁。

- 状态模块

   它管理工作流状态生命周期。 我们支持事件相关的状态，下面支持的状态列表是 Work-in-Progress。

   | 工作流状态 | 说明 |
   | --- | --- |
   | 操作 | 执行 Actions | 中定义的 AsyncAPI 函数
   | 活动 | 检查定义的事件是否匹配，如果匹配则执行定义的 AsyncAPI 函数 |
   | 切换 | 检查事件是否与事件条件匹配，并执行定义的 AsyncAPI 函数 |
   | 并行 | 并行执行定义的 AsyncAPI 函数 |
   | 为每个 | 迭代 inputCollection 并执行定义的 AsyncAPI 函数 |

- 动作模块

   它管理动作中的函数。

- 功能模块

   它通过在 EventMesh Runtime 中创建发布者和/或订阅者来管理 AsyncAPI 函数，并管理发布者/订阅者的生命周期。

     | AsyncAPI 操作 | EventMesh 运行时 |
     | --- | --- |
     | 发布 | 出版商 |
     | 订阅 | 订户 |

- 事件模块

   它使用工作流 DSL 中定义的规则管理 CloudEvents 数据模型，包括事件过滤器、关联和转换。

- 重试模块

   它管理事件发布到 EventMesh Runtime 的重试逻辑。
