# EventMesh SchemaRegistry (OpenSchema)

## Introduction

[EventMesh(incubating)](https://github.com/apache/incubator-eventmesh) is a dynamic cloud-native eventing infrastructure.

## An Overview of Schema and Schema Registry

### Schema

A Schema stands for the description of serialization instances(string/stream/file/...) and has two properties. First, it is also in the format of serialization type. Second, it defines what requirements such serialized instances should satisfy. 

Besides describing a serialization instance, a Schema may also be used for validating whether an instance is legitimate. The reason is that it defines the ```type```(and other properties) of a serialized instance and inside keys. Taking JSON Schema for example, it could not only be referred when describing a JSON string, but also be used for validating whether a string satisfies properties defined in the schema[[1]](#References).

Commonly, there are JSON Schema, Protobuf Schema, and Avro Schema, representing description of JSON instances, Protobuf instances, and Avro instances respectively.


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
| F-1            | A message from producer could be understood(known serialization type) by consumer without a contract between each other. | Functionality |
| F-2            | The message content from producer could be validated whether serialized correctly according to consumer's schema. | Functionality |


## Design Details

### Architecture

![OpenSchema](https://user-images.githubusercontent.com/28994988/129255292-e61acc87-5250-4be5-ac9c-a099f6ef157c.png)

### LifeCycle of Schema

The highlevel lifecycle of schema in messages undergoes 9 steps as follows:
- step1: Producer registers a schema to OpenSchema Registry through OpenSchema service plugin.
- step2: Producer receives schema id from OpenSchema Registry.
- step3: Producer patch the schema id in front of messages and send them to EventMesh.
- step4: In a load-balanced way, EventMesh validate whether the format of messages is correct. Here, the load-balanced way means that the EventMesh, which is the entry port of messages, validates messages one skiping one and the rest messages are validated in the EventMesh which is the outer port.
- step5: EventMesh patch a status(true/false) for validation in front of schema id and router it to the EventMesh where the outer port exists.
- step6: EventMesh validates those non-validated messages.
- step7: EventMesh unpatch validation status and send [schema id + message] to the consumer.
- step8: Consumer unpatch the schema id and retreive the schema from OpenSchema Registry.
- step9: Consumer de-serialize messages according to schema.


## References
[1] [schema validator (github.com)](https://github.com/search?q=schema+validator)
[2] [EMQ : Schema Registry](https://www.jianshu.com/p/33e0655c642b)
[3] [Pulsar : Schema Registry](https://mp.weixin.qq.com/s/PaB66-Si00cX80py5ig5Mw)
[4] [confluentinc/schema-registry](https://github.com/confluentinc/schema-registry)
[5] [openmessaging/openschema](https://github.com/openmessaging/openschema)
 
