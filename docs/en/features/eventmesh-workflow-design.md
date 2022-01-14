# EventMesh Workflow

## Business Problem

Imaging you are building a simple Order Management System for an E-Commerce Store. The system is able to taking new orders from store website,
and process the orders, handle the payments, and finally process the shipment.

For the high availability and high performance, you architect the system using event-driven architecture (EDA), and build microservice apps to handle
store frontend, order management, payment processing, and shipment management.
You deploy the whole system in a cloud environment. To handle the high workload, you leverage the messaging system to buffer the loads,
and scale up multiple instances of microservices. The architecture looks similar to the followings:

![eventmesh-workflow-uc](../../images/features/eventmesh-workflow-usecase.jpg?raw=true)

While each microservice is acting on its own event channels, EventMesh plays a crucial role of doing Event Orchestration.

We use CNCF Serverless Workflow to describe this Event Workflow Orchestration.

## About CNCF Serverless Workflow

CNCF Serverless Workflow defines a vendor-neutral, open-source, and fully community-driven ecosystem
for defining and running DSL-based workflows that target the Serverless technology domain.

Serverless workflow has Domain Specific Language (DSL) to describe a workflow orchestration of serverless functions and microservices.

More about this can be found in its [official github site](https://github.com/serverlessworkflow/specification)

## EventMesh Workflow

We leverage Serverless Workflow DSL to describe the EventMesh workflow. Based on its spec, the workflow is consists of a serials of states.
At this moment we only support event related states. See the supported states in [Workflow DSL Design](#workflow-dsl-design-wip).

In the workflow `state`, it has the applicable `Action` to perform. The `Action` has reference to the `Function` definition.
In EDA solution, we usually defined the event-driven microservice using AsyncAPI.
Serverless workflow supports AsyncAPI as the `Function` definition.
See [Using Funtions for AsyncAPI Service](https://github.com/serverlessworkflow/specification/blob/main/specification.md#using-functions-for-async-api-service-invocations)

### AsyncAPI

AsyncAPI is an open source initiative that seeks to improve the current state of Event-Driven Architectures (EDA).
Our long-term goal is to make working with EDAs as easy as it is to work with REST APIs.
That goes from documentation to code generation, from discovery to event management.
Most of the processes you apply to your REST APIs nowadays would be applicable to your event-driven/asynchronous APIs too.

See AsyncAPI detail in the [official site](https://www.asyncapi.com/docs/getting-started)

### Workflow Example

In this example, we build the event-driven workflow of the Order management system above.

First, we need the AsyncAPI definitions of the microservice apps. 

- Online Store App

```yaml
asyncapi: 2.2.0
info:
  title: Online Store application
  version: '0.1.0'
channels:
  store/order:
    subscribe:
      operationId: receivedStoreOrder
      message:
        name: StoreOrder
```

- Order Service

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
        name: Order
  order/outbound:
    subscribe:
      operationId: receivedPurchaseOrder
      message:
        name: PurchaseOrder
```

- Payment Service

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
        name: Payment
  payment/outbound:
    subscribe:
      operationId: receivedPaymentReceipt
      message:
        name: PaymentReceipt
```

- Shipment Service

```yaml
asyncapi: 2.2.0
info:
  title: Shipment Service
  version: '0.1.0'
channels:
  shipmet/inbound:
    publish:
      operationId: sendShipment
      message:
        name: Shipment
```

Then we write the workflow using Serverless Workflow DSL.

```yaml
id: StoreOrderWorkflow
version: '1.0'
specVersion: '0.8'
name: Store Order Management Workflow
start: Received Store Order
functions:
  - name: receivedStoreOrder
    operation: file://onlineStoreApp.yaml#receivedStoreOrder
  - name: dispatchOrder
    operation: file://orderService.yaml#sendOrder
  - name: recievedPO
    operation: file://orderService.yaml#receivedPurchaseOrder
  - name: dispatchPayment
    operation: file://paymentService.yaml#sendPayment
  - name: receivedReceipt
    operation: file://paymentService.yaml#receivedPaymentReceipt
  - name: dispatchShipment
    operation: file://shipmentService.yaml#sendShipment
events:
  - name: StoreOrder
    type: StoreOrderType
    source: OnlineStoreApp
  - name: PurchaseOrder
    type: PurchaseOrderType
    source: OrderService
  - name: PaymentReceipt
    type: PaymentReceiptType
    source: PaymentService
  - name: PaymentDenial
    type: PaymentDenialType
    source: PaymentService
states:
- name: Received Store Order
  type: operation
  actions:
    - functionRef:
        refName: receivedStoreOrder
  transition: Process Store Order
- name: Process Store Order
  type: event
  onEvents:
    - eventRefs:
        - StoreOrder
      actionMode: sequential
      actions:
        - functionRef:
            refName: dispatchOrder
        - functionRef:
            refName: recievedPO
  transition: Process Order Payment
- name: Process Order Payment
  type: event
  onEvents:
    - eventRefs:
        - PurchaseOrder
      actionMode: sequential
      actions:
        - functionRef:
            refName: dispatchPayment
        - functionRef:
            refName: receivedReceipt
  transition: Check Payment Receipt
- name: Check Payment Receipt
  type: switch
  eventConditions:
    - eventRef: PaymentReceipt
      transition: Process Order Shipment
    - eventRef: PaymentDenial
      end: true
  defaultCondition
    end: true
- name: Process Order Shipment
  type: operation
  action:
    - functionRef:
        refName: dispatchShipment    
  end: true
```

## EventMesh Workflow Engine

![eventmesh-workflow-arch](../../images/features/eventmesh-workflow-arch.jpg?raw=true)

## EventMesh Workflow Engine Design

EventMesh Workflow Engine consists of the following modules:

- Workflow Parser
  
  Using the SDK provided by Serverless Workflow community (see supported [SDKs](https://github.com/serverlessworkflow/specification#sdks)),
  parse and validated the workflow DSL inputs, and generate workflow definition.
  

- AsyncAPI Parser
 
  Using the SDK provided by AsyncAPI community (see [tool list](https://www.asyncapi.com/docs/community/tooling)),
  parse and validated the AsyncAPI yaml inputs, and generate the AsyncAPI definition.


- Workflow Module
  
  It manages a workflow instance life cycle, from create, start, stop to destroy.


- State Module

  It manages workflow state life cycle. We support the event-related states, and the supported state list below is Work-in-Progress.

  | Workflow State | Description | 
  | --- | --- |
  | Operation | Execute the AsyncAPI functions defined in the Actions |
  | Event | Check if the defined Event matched, if so execute the defined AsyncAPI functions |
  | Switch | Check the event is matched with the event-conditions, and execute teh defined AsyncAPI functions |
  | Parallel | Execute the defined AsyncAPI functions in parallel |
  | ForEach | Iterate the inputCollection and execute the defined AsyncAPI functions |
  
- Action Module

  It managed the functions inside the action. 


- Function Module
 
  It manages the AsyncAPI functions by creating the publisher and/or subscriber in EventMesh Runtime, and manage the publisher/subscriber life cycle.

    | AsyncAPI Operation | EventMesh Runtime | 
    | --- | --- | 
    |  Publish | Publisher | 
    | Subscribe | Subscriber |


- Event Module

  It manages the CloudEvents data model, including event filter, correlation and transformation using the rules defined in the workflow DSL.


- Retry Module

  It manages the retry logic of the event publishing into EventMesh Runtime.
