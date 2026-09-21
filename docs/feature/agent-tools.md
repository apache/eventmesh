# Agent Tools & Event Triggers

For users who want an EventMesh-hosted agent to **act on** the mesh — deliver
LLM decisions through connectors, poll external systems mid-conversation, or
run agents fully event-driven without a human in the loop. Covers the
`eventmesh-agent` extension points (LLM client, conversation memory, tools,
event triggers) and how they map onto the connector plugin ecosystem.
*(Experimental)*

---

## The extension surface

`StreamingAgent` is assembled from constructor-injected interfaces — every
piece below has a shipped default and can be replaced without forking:

| Extension point | Interface | Default | Replace it to… |
| --- | --- | --- | --- |
| Chat backend | `agent.llm.LlmClient` | `OpenAiLlmClient` (OpenAI-compatible SSE) | call Anthropic native, Bedrock, or an internal inference service |
| Conversation history | `agent.ConversationMemory` | `ConversationStore` (in-memory window) | persist sessions to Redis / RocksDB / a database |
| Callable tools | `agent.tool.AgentTool` | none registered (plain streaming) | expose any capability to the model via function calling |
| Event triggers | `StreamingAgent.onEvent(...)` | off | drive the agent from topic subscriptions instead of user prompts |

## Connector plugins as agent tools

`ConnectorToolAdapter` turns the shipped connector SPI into `AgentTool`s, so
every connector plugin doubles as a tool the LLM may call:

- **Sink tool (write)** — the model's arguments object is wrapped as one
  CloudEvent and pushed through `SinkConnector.put()`. Example: a DingTalk
  sink connector becomes a `notify` tool the model can call to alert a
  channel.
- **Source tool (read)** — each call polls one batch via
  `SourceConnector.poll()` and returns it as a JSON array. Example: a canal
  source connector becomes a `query-changes` tool for mid-conversation data
  lookups.

Wire-up in `AgentApplication` (system properties):

```properties
# a write tool backed by any sink connector on the classpath
agent.tools.sink.notify=org.apache.eventmesh.connector.dingtalk.sink.DingTalkSinkConnector
agent.tools.props.notify.connector.sink.webhook=https://oapi.dingtalk.com/robot/send?...

# a read tool backed by any source connector
agent.tools.source.query-changes=org.apache.eventmesh.connector.canal.source.CanalSourceConnector
agent.tools.props.query-changes.connector.batchSize=10
```

With tools registered the agent switches from plain token streaming to a
**function-calling loop**: the model may request tools, receives their
results as messages, and continues until it produces a final answer (bounded
at 5 tool iterations). Without tools, behavior is unchanged
token-by-token streaming.

## SPI-deployed custom tools

Beyond connector-backed tools, custom tools follow the repo-standard plugin
mechanism (same as storage/connector plugins):

1. Implement `agent.tool.AgentTool` (name + description + JSON schema +
   `invoke`), no extra annotation needed on your class.
2. In your jar add a service file
   `META-INF/eventmesh/org.apache.eventmesh.agent.tool.AgentTool` containing
   `mytool=com.example.MyTool`.
3. Drop the jar into the agent's `plugin/agent/` directory (the launcher
   puts every jar there on the classpath) and enable it with
   `AGENT_TOOLS_SPI=mytool` (i.e. `-Dagent.tools.spi=mytool`; comma-list
   supported).

Resolution goes through `EventMeshExtensionFactory` — the same loader that
serves storage and connector plugins, so singleton semantics and the
`META-INF/eventmesh/` convention are identical.

## Event-driven agents (no user in the loop)

Set a subscription list and an output topic:

```properties
agent.subscribe.topics=orders.changed,risk.alerts
agent.trigger.output.topic=agent.decisions
```

Each consumed CloudEvent becomes a prompt, is answered (with tools if
registered), and the answer is published onto the output topic — where any
**sink connector** can deliver it (DingTalk, Kafka, Spring, ...). Trigger
conversations are keyed `trigger:<eventId>` and never bleed into user
sessions. This is the agent-side twin of the connector pipeline:

```
source connector ──> topic ──> agent (trigger) ──> LLM (+ tools)
                                  │
                                  └── output topic ──> sink connector ──> external system
```

## Using the interfaces directly (embedder API)

```java
LlmClient llm = new OpenAiLlmClient(baseUrl, apiKey, model); // or your own impl
ConversationMemory memory = new ConversationStore(20);        // or a persistent impl
ToolRegistry tools = new ToolRegistry()
    .register(ConnectorToolAdapter.sinkTool("notify", "Send a DingTalk notification",
        DingTalkSinkConnector.class, props));
StreamingAgent agent = new StreamingAgent(client, parent, agentId, llm, memory, tools);
```

## Configuration reference

| Key | Default | Meaning |
| --- | --- | --- |
| `agent.tools.spi` | (empty) | Comma list of SPI names resolved via `EventMeshExtensionFactory` (jars in `plugin/agent/`) |
| `agent.tools.sink.<name>` | — | FQCN of a `SinkConnector` exposed as write tool `<name>` |
| `agent.tools.source.<name>` | — | FQCN of a `SourceConnector` exposed as read tool `<name>` |
| `agent.tools.props.<name>.*` | — | Connector init properties for tool `<name>` |
| `agent.subscribe.topics` | (empty) | Comma-separated topics consumed as event triggers |
| `agent.trigger.output.topic` | `agent.triggers` | Topic the trigger answers are published to |

The agent connects to the runtime like any SDK client (`agent.runtime.url`,
default `http://localhost:10105`).

## Where the code lives

| Piece | Location |
| --- | --- |
| LLM SPI + OpenAI default | `eventmesh-agent/.../agent/llm/{LlmClient,OpenAiLlmClient}.java` |
| Memory SPI + in-memory default | `eventmesh-agent/.../agent/{ConversationMemory,ConversationStore}.java` |
| Tool SPI + registry + adapter | `eventmesh-agent/.../agent/tool/{AgentTool,ToolRegistry,ConnectorToolAdapter}.java` |
| Tool loop + event trigger path | `eventmesh-agent/.../agent/StreamingAgent.java` |
| Boot wiring (tools + triggers) | `eventmesh-agent/.../agent/AgentApplication.java` |
| Tests | `eventmesh-agent/src/test/.../tool/*`, `.../llm/OpenAiLlmClientChatTest.java` |
