# Schema Integration in EventMesh
## ISSUE
[Integrate With OpenSchema #339](#https://github.com/apache/incubator-eventmesh/issues/339)

## Goals
- ensure a message could be understood by both producer and consumer
- validate a message is serialized correctly

## Comparison of Schema Integration in EventMesh and Other Projects
Project | Application
:---: | :---
EMQ | use "Schema Registry" and "Rule Matching" to transfer a message from one serialization format to another
Pulsar | use "Schema Registry" to validate a message
Kafka(Confluent) | satisfy the aforementioned two goals, inside which the second one is optional
EventMesh | satisfy the aforementioned two goals

## Archetecture
![OpenSchema](https://user-images.githubusercontent.com/28994988/129255292-e61acc87-5250-4be5-ac9c-a099f6ef157c.png)
## LifeCycle of Schema in Messages
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

## OpenSchema Restful
Referenced from [openmessaging/openschema](#https://github.com/openmessaging/openschema).

Method | URL | RequestBody | ResponseBody
:--- | :--- | :--- | :---
**Subject**|
POST | /subjects/{string:subject} | {subject:{JSON:subject}} | {subject:{JSON:subject}}
GET | /subjects | None | {subjects:{JSONArray:subjects}}
GET | /subjects/{string:subject} | None | {subject:{JSON:subject}}
GET | /schemas/ids/{string:id}/subjects | None | {subject:{JSON:subject}}
DELETE | /subjects/{string:subject} | None | [versions:{JSONArray:versions}]
**Schema**|
POST | /subjects/{string:subject}/versions | {schema:{JSON:schema}} | {id:{string:id}}
GET | /schemas/ids/{string:id} | None | {schema:{JSON:schema}}
GET | /subjects/{string:subject}/versions/{int:version}/schema | None | {schema:{JSON:schema}}
GET | /subjects/{string:subject}/versions | None | [versions:{JSONArray:versions}]
DELETE | /subjects/{string:subject}/versions/{int:version} | None | version:{int:version}
**Compatibility**|
POST | /compatibility/subjects/{string:subject}/versions/{int:version} | {schema:{JSON:schema}} | {is_compatible:{boolean:is_compatible}}
PUT | /config/{string:subject} | {"compatibility":{string:compatibility}} | {"compatibility":{string:compatibility}}
GET | /config/{string:subject} | None | {"compatibility":{string:compatibility}}