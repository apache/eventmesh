# EventMesh工作流

## 业务场景

图中你正在构建一个简单的电商订单管理系统，系统能够接收和调配新的订单，调配流程需要处理所有的订单创建，付款处理以及发货处理。

为了实现高可用和高性能，你可以使用事件驱动架构（EDA）构建微服务应用去处理商店前端，订单管理，支付处理和发货管理。你可以在云上部署整个系统。要处理高并发，你可以利用消息系统缓冲，并扩展多个微服务实例。架构类似于：

![Workflow Use Case](/images/design-document/workflow-use-case.jpg)

当每个微服务都在自己的事件通道上运行时，EventMesh在执行事件编排方面发挥着至关重要的作用。

我们使用 [CNCF Serverless工作流](https://serverlessworkflow.io/) 来描述此事件工作流编排。

## CNCF Serverless工作流

CNCF Serverless工作流定义了一个厂商中立、开源和完全社区驱动的生态系统，用于定义和运行针对Serverless技术领域的基于DSL的工作流。

Serverless工作流定义了一种领域特定语言（DSL）来描述有状态和无状态的基于工作流的serverless函数和微服务编排。

详见[官方github](https://github.com/serverlessworkflow/specification)

## EventMesh工作流

我们利用Serverless工作流DSL来描述EventMesh工作流。根据其规范，工作流由一系列用于描述控制流逻辑的工作流状态组成。目前，我们仅支持与事件相关的工作流状态。请参见[工作流DSL设计](#workflow-dsl-design-wip)中支持的状态。

`工作流状态`可以包含通用的`操作`，或在工作流执行期间应调用的服务/函数。这些`操作`可以引用可复用的`函数`定义（应如何调用这些函数/服务），还可以引用触发基于事件的服务调用的事件，以及要等待的事件，这些事件表示这种基于事件的服务调用完成。

在EDA解决方案中，我们通常使用AsyncAPI定义事件驱动的微服务。Serverless工作流“函数”定义支持使用AsyncAPI定义调用语义。有关详细信息，请参见[Using Funtions for AsyncAPI Service](https://github.com/serverlessworkflow/specification/blob/main/specification.md#using-functions-for-async-api-service-invocations)。

### AsyncAPI

AsyncAPI是一项开源计划，旨在改善事件驱动体系结构（EDA）的当前状态。我们的长期目标是让使用EDA和使用REST API一样容易。包括从文档到代码生成、发现到事件管理。现在应用于REST API的大多数流程也适用于事件驱动/异步API。

详见[AsyncAPI官网](https://www.asyncapi.com/docs/getting-started)

### 工作流示例

在本示例中，我们构建了上面订单管理系统的事件驱动工作流。

首先，我们需要为我们的微服务应用定义AsyncAPI。

- 在线商店应用程序

```yaml
asyncapi: 2.2.0
info:
  title: Online Store application
  version: '0.1.0'
channels:
  store/order:
    subscribe:
      operationId: newStoreOrder
      message:
        $ref : '#/components/NewOrder'

```

- 订单服务

```yaml
asyncapi: 2.2.0
info:
  title: Order Service
  version: '0.1.0'
channels:
  order/inbound:
    publish:
      operationId: sendOrder
      message:
        $ref : '#/components/Order'
  order/outbound:
    subscribe:
      operationId: processedOrder
      message:
        $ref : '#/components/Order'
```

- 支付服务

```yaml
asyncapi: 2.2.0
info:
  title: Payment Service
  version: '0.1.0'
channels:
  payment/inbound:
    publish:
      operationId: sendPayment
      message:
        $ref : '#/components/OrderPayment'
  payment/outbound:
    subscribe:
      operationId: paymentReceipt
      message:
        $ref : '#/components/OrderPayment'
```

- 物流服务

```yaml
asyncapi: 2.2.0
info:
  title: Shipment Service
  version: '0.1.0'
channels:
  shipment/inbound:
    publish:
      operationId: sendShipment
      message:
        $ref : '#/components/OrderShipment'
```

接下来，定义描述订单管理业务逻辑的订单工作流。

```yaml
id: storeorderworkflow
version: '1.0'
specVersion: '0.8'
name: Store Order Management Workflow
states:
  - name: Receive New Order Event
    type: event
    onEvents:
      - eventRefs:
          - NewOrderEvent
        actions:
          - eventRef:
              triggerEventRef: OrderServiceSendEvent
              resultEventRef: OrderServiceResultEvent
          - eventRef:
              triggerEventRef: PaymentServiceSendEvent
              resultEventRef: PaymentServiceResultEvent
    transition: Check Payment Status
  - name: Check Payment Status
    type: switch
    dataConditions:
      - name: Payment Successfull
        condition: "${ .payment.status == 'success' }"
        transition: Send Order Shipment
      - name: Payment Denied
        condition: "${ .payment.status == 'denied' }"
        end: true
    defaultCondition:
      end: true
  - name: Send Order Shipment
    type: operation
    actions:
      - eventRef:
          triggerEventRef: ShipmentServiceSendEvent
    end: true
events:
  - name: NewOrderEvent
    source: file://onlineStoreApp.yaml#newStoreOrder
    type: asyncapi
    kind: consumed
  - name: OrderServiceSendEvent
    source: file://orderService.yaml#sendOrder
    type: asyncapi
    kind: produced
  - name: OrderServiceResultEvent
    source: file://orderService.yaml#processedOrder
    type: asyncapi
    kind: consumed
  - name: PaymentServiceSendEvent
    source: file://paymentService.yaml#sendPayment
    type: asyncapi
    kind: produced
  - name: PaymentServiceResultEvent
    source: file://paymentService.yaml#paymentReceipt
    type: asyncapi
    kind: consumed
  - name: ShipmentServiceSendEvent
    source: file://shipmentService.yaml#sendShipment
    type: asyncapi
    kind: produced
```

对应的工作流图如下：

![Workflow Diagram](/images/design-document/workflow-diagram.png)

## EventMesh工作流引擎

在下面的体系结构图中, EventMesh目录, EventMesh工作流引擎 和 EventMesh Runtime在三个不同的处理器中运行。

![Workflow Architecture](/images/design-document/workflow-architecture.jpg)

运行工作流的步骤如下：

1. 在环境中部署发布者和订阅者应用程序。
   使用AsyncAPI描述应用程序API，生成asyncAPI yaml。
   使用AsyncAPI在EventMesh目录中注册发布者和订阅者应用程序。

2. 在EventMesh工作流引擎中注册Serverless工作流DSL。

3. 工作流引擎从EventMesh目录查询发布服务器和订阅服务器的需要的工作流DSL`函数`。

4. 事件驱动App将事件发布到EventMesh Runtime触发工作流。EventMesh工作流引擎发布和订阅事件、编排事件。

### EventMesh Catalog 设计

EventMesh目录存储发布者、订阅者和通道元数据。由以下模块组成：

- AsyncAPI解析器

  使用AsyncAPI社区提供的SDK ([tool list](https://www.asyncapi.com/docs/community/tooling)),
  解析并验证AsyncAPI yaml输入，并生成AsyncAPI定义。

- 发布者, 通道, 订阅者模块

  从AsyncAPI定义存储发布者、订阅者和通道信息。

### EventMesh工作流引擎设计

工作流引擎由以下模块组成：

- 工作流解析器

  使用Serverless Workflow社区提供的SDK([SDKs](https://github.com/serverlessworkflow/specification#sdks)),
  解析和验证工作流DSL输入，并生成工作流定义。

- 工作流模块

  管理工作流实例的生命周期，从创建、启动、停止到销毁。

- 状态模块

  管理工作流状态生命周期。支持与事件相关的状态，and the supported state list below is Work-in-Progress.

  | 工作流状态 | 描述 |
  | --- | --- |
  | Operation | 执行Actions中定义的AsyncAPI函数 |
  | Event | 检查定义的事件是否匹配，如果匹配，执行定义的AsyncAPI函数 |
  | Switch | 检查事件是否与事件条件匹配，并执行定义的AsyncAPI函数 |
  | Parallel | 并行执行定义的AsyncAPI函数 |
  | ForEach | 迭代输入集合并执行定义的AsyncAPI函数 |

- 行为模块

  管理函数中的行为。

- 函数模块

  通过在EventMesh Runtime中创建发布者和/或订阅者来管理AsyncAPI函数，并管理发布者/订阅者生命周期。

  | AsyncAPI 操作 | EventMesh Runtime |
  | --- | --- |
  |  Publish | Publisher |
  | Subscribe | Subscriber |

- 事件模块

  使用工作流DSL中定义的规则管理CloudEvent数据模型，包括事件过滤器、关联和转换。

- 重试模块

  管理事件发布到EventMesh Runtime的重试逻辑。
