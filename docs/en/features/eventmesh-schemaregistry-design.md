# EventMesh SchemaRegistry (OpenSchema)

## Introduction

[EventMesh(incubating)](https://github.com/apache/incubator-eventmesh) is a dynamic cloud-native eventing infrastructure.

## An Overview of Schema and Schema Registry

### Schema

A Schema stands for the description of serialization instances(string/stream/file/...) and has two properties. First, it is also in the format of serialization type. Second, it defines what requirements such serialized instances should satisfy. 

Besides describing a serialization instance, a Schema may also be used for validating whether an instance is legitimate. The reason is that it defines the ```type```(and other properties) of a serialized instance and inside keys. Taking JSON Schema for example, it could not only be referred when describing a JSON string, but also be used for validating whether a string satisfies properties defined in the schema[[1]](#References).

Commonly, there are JSON Schema, Protobuf Schema, and Avro Schema.

### Schema Registry

Schema Registry is a server provides RESTful interfaces. It could receive and store Schemas from clients, as well as provide intrefaces for other clients to retrieve Schemas from it. 

It could be applied to validation process and (de-)serialization process.

### A Comparison of Schema Registry in Other Projects

Project | Application
:---: | :---
EMQ[[2]](#References) | Mainly in (de-)serialization process. Use "Schema Registry" and "Rule Matching" to transfer a message from one serialization format to another.
Pulsar[[3]](#References) | Mainly in validation process. Use "Schema Registry" to validate a message.
Confluentinc[[4]](#References) | In both validation and (de-)serialization process.

## An Overview of OpenSchema

OpenSchema[[5]](#References) proposes a specification for data schema when exchanging the message and event in more and more modern cloud-native applications. It designs a RESTful interface for storing and retrieving such as Avro, JSON Schema, and Protobuf3 schemas from three aspects(subject/schema/compatibility).


## Requirements(Goals)

| Requirement ID | Requirement Description                                      | Comments      |
| :------------- | ------------------------------------------------------------ | ------------- |
| F-1            | In transmission, no message needs to contain schema information which bring efficiency. | Functionality |
| F-2            | The message content from producer could be validated whether serialized correctly according to schema. | Functionality |


## Design Details

### Architecture

![OpenSchema](../../images/features/eventmesh-schemaregistry-arch.png?raw=true)

### Process of Transferring Messages under Schema Registry

![Process](../../images/features/eventmesh-schemaregistry-process.jpg)

The highlevel process of messages transmission undergoes 10 steps as follows:
- step1: Consumer subscribes "TOPIC" messages from EventMesh.
- step2: Producer registers a schema to EventMesh.
- step3: EventMesh registers a schema to Schema Registry.
- step4: Schema Registry returns the id of newly created schema; EventMesh caches such id and schema.
- step5: EventMesh returns the id of schema to Producer.
- step6: Producer patches the id in front of messages and send messages to EventMesh.
- step7: EventMesh validates the messages in the entry port and send it to EventStore; EventMesh retrieves messages from EventStore.
- step8: EventMesh unpatches the id and send it to Schema Registry(if such <id, schema> does not exists in local cache).
- step9: Schema Registry returns schema and EventMesh caches it.
- step10: EventMesh patches schema in front of messages and push it to consumer.


## References
[1] [schema validator (github.com)](https://github.com/search?q=schema+validator)

[2] [EMQ : Schema Registry](https://www.jianshu.com/p/33e0655c642b)

[3] [Pulsar : Schema Registry](https://mp.weixin.qq.com/s/PaB66-Si00cX80py5ig5Mw)

[4] [confluentinc/schema-registry](https://github.com/confluentinc/schema-registry)

[5] [openmessaging/openschema](https://github.com/openmessaging/openschema)
 
