# EventMesh Agent (v2 Streaming Agent Process)

<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License. You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

The `eventmesh-agent` module is the v2 streaming-agent process: an independent JVM that
registers with a running EventMesh runtime, subscribes its private lite channel, and bridges
routed prompts to an OpenAI-compatible LLM gateway — streaming tokens back over the runtime
to the requesting client.

## Boot sequence

1. **Register** — `POST /agent/register` on the runtime traffic port; the runtime assigns the
   agent its `agent-parent` + `client-reply-parent` (§5.2 of the v2 design).
2. **Subscribe** — the agent subscribes `agent.<agentId>` on its parent via the lite wire
   (`subscribeLiteBytes`).
3. **Ready** — `POST /agent/ready` flips the registration ready; only now does matchmaking
   route sessions to this agent (ready-before-route).
4. **Heartbeat** — a virtual thread refreshes the TTL and reports active sessions every
   `agent.heartbeat.intervalMs`.

## Quick start

```shell
# 1. runtime (defaults: memory storage, traffic 10105)
./gradlew :eventmesh-runtime:runRuntime   # or use the docker image

# 2. agent (needs an OpenAI-compatible endpoint)
LLM_BASE_URL=https://api.openai.com LLM_API_KEY=sk-... LLM_MODEL=gpt-4o-mini   ./bin/start-agent.sh                    # from dist-agent/
```

Dev runner without a distribution: `./gradlew :eventmesh-agent:runAgent -Dllm.api.key=sk-...`.

## Configuration

All config is `-D` system properties; `bin/start-agent.sh` maps the `AGENT_*` / `LLM_*` env
vars (see `conf/agent.properties` for the full list).

| Key | Default | Description |
|---|---|---|
| `agent.runtime.url` | `http://localhost:10105` | Runtime traffic URL (control plane + lite wire) |
| `agent.id` | `agent-<ts>` | Agent identity; must be unique per process |
| `agent.capacity` | `100` | Advertised concurrent-stream capacity (matchmaking input) |
| `agent.heartbeat.intervalMs` | `10000` | Heartbeat cadence |
| `agent.heartbeat.failLimit` | `6` | Consecutive heartbeat failures before the process exits (supervisor restarts it) |
| `agent.conversation.maxHistory` | `20` | Per-conversation message sliding window |
| `agent.conversation.maxConversations` | `1000` | Live-conversation bound; least-recently-used conversations are evicted |
| `llm.base.url` | `https://api.openai.com` | OpenAI-compatible gateway base URL |
| `llm.api.key` | _(empty)_ | Bearer key — **required**; empty fails fast at boot |
| `llm.api.key.optional` | `false` | Opt-out of the empty-key fail-fast (mock gateways) |
| `llm.model` | `gpt-4o-mini` | Default model; per-request model overrides win |

## Reliability behavior

- **Fail-fast on empty LLM key** — an agent without a usable key would fail every routed
  request after registering READY, so boot refuses (unless `llm.api.key.optional=true`).
- **Heartbeat failure limit** — after `agent.heartbeat.failLimit` consecutive failures the
  process exits nonzero (the runtime TTL has evicted the registration by then; a zombie
  serves nothing). Supervisors (systemd / K8s) restart it.
- **Bounded conversations** — each conversation keeps a sliding window of turns, and the
  store evicts least-recently-used conversations past `agent.conversation.maxConversations`,
  bounding agent memory on long-lived processes.

## Limitations

- Conversation history is process-local (lost on restart); persistence is an explicit TODO.
- Mode-1 (streaming calls) only — mode-2 pub/sub sessions are not routed to agents.
- One LLM gateway per process (`llm.base.url`); multi-provider routing is future work.
