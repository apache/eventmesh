# EventMesh Core Engines Configuration Guide

EventMesh provides powerful core engines (`Filter`, `Transformer`, `Router`) to dynamically process messages. These engines are configured via **MetaStorage** (Governance Center, e.g., Nacos, Etcd), supporting on-demand loading and hot-reloading.

## 0. Core Concepts

Before configuration, it is important to understand the specific role of each engine in the message flow:

*   **Filter (The Gatekeeper)**: Decides **"Whether to pass"**.
    *   It inspects the message (CloudEvent) attributes. If the message matches the rules, it passes; otherwise, it is dropped.
    *   *Use Case*: Block debug logs from production traffic; Only subscribe to specific event types.

*   **Transformer (The Translator)**: Decides **"What it looks like"**.
    *   It modifies the message content (Payload or Metadata) according to templates or scripts.
    *   *Use Case*: Convert XML to JSON; Mask sensitive data (PII); Adapt legacy protocols to new standards.

*   **Router (The Dispatcher)**: Decides **"Where to go"**.
    *   It dynamically changes the destination (Topic) of the message.
    *   *Use Case*: Route traffic to a Canary/Gray release topic; Route high-priority orders to a dedicated queue.

---

## 1. Overview

The configuration is not in local property files but distributed via the MetaStorage. EventMesh listens to specific **Keys** based on client Groups.

- **Data Source**: Configured via `eventMesh.metaStorage.plugin.type`.
- **Loading Mechanism**: Lazy loading & Hot-reloading.
- **Key Format**: `{EnginePrefix}-{GroupName}`.
- **Value Format**: JSON Array.

| Engine | Prefix | Scope | Description |
| :--- | :--- | :--- | :--- |
| **Router** | `router-` | Pub Only | Routes messages to different topics. |
| **Filter** | `filter-` | Pub & Sub | Filters messages based on CloudEvent attributes. |
| **Transformer** | `transformer-` | Pub & Sub | Transforms message content (Payload/Header). |

---

## 2. Router (Routing)

**Scope**: Publish Only (Upstream)  
**Key**: `router-{producerGroup}`

Decides the target storage topic for a message sent by a producer.

### Configuration Example (JSON)

```json
[
  {
    "topic": "original-topic",
    "routerConfig": {
      "targetTopic": "redirect-topic",
      "expression": "data.type == 'urgent'"
    }
  }
]
```

*   **topic**: The original topic the producer sends to.
*   **targetTopic**: The actual topic to write to Storage.
*   **expression**: Condition to trigger routing (e.g., SpEL).

---

## 3. Filter (Filtering)

**Scope**: Both Publish (Upstream) & Subscribe (Downstream)

### A. Publish Side (Upstream)
**Key**: `filter-{producerGroup}`  
**Effect**: Intercepts messages **before** they are sent to Storage.

### B. Subscribe Side (Downstream)
**Key**: `filter-{consumerGroup}`  
**Effect**: Intercepts messages **before** they are pushed to the Consumer.

### Configuration Example (JSON)

```json
[
  {
    "topic": "test-topic",
    "filterPattern": {
      "source": ["app-a", "app-b"],
      "type": [{"prefix": "com.example"}]
    }
  }
]
```

*   **filterPattern**: Rules matching CloudEvent attributes. If a message doesn't match, it is dropped.

---

## 4. Transformer (Transformation)

**Scope**: Both Publish (Upstream) & Subscribe (Downstream)

### A. Publish Side (Upstream)
**Key**: `transformer-{producerGroup}`  
**Effect**: Modifies message content **before** sending to Storage.

### B. Subscribe Side (Downstream)
**Key**: `transformer-{consumerGroup}`  
**Effect**: Modifies message content **before** pushing to the Consumer.

### Configuration Example (JSON)

```json
[
  {
    "topic": "raw-topic",
    "transformerConfig": {
      "transformerType": "template",
      "template": "{\"id\": \"${id}\", \"new_content\": \"${data.content}\"}"
    }
  }
]
```

*   **transformerType**: e.g., `original`, `template`.
*   **template**: The transformation template definition.

---

## 5. Verification

1.  **Publish Config**: Add the JSON config to your Governance Center (e.g., Nacos) with the Data ID `router-MyGroup`.
2.  **Send Message**: Use EventMesh SDK to send a message from `MyGroup`.
3.  **Observe**:
    *   For **Router**: Check if the message appears in the `targetTopic` in your MQ.
    *   For **Filter**: Check if blocked messages are skipped.
    *   For **Transformer**: Check if the message body in MQ (for Pub) or Consumer (for Sub) is modified.
