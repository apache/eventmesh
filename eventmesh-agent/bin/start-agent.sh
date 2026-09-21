#!/usr/bin/env bash

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# EventMesh agent launcher (v2 streaming-agent process).
#
# Boots org.apache.eventmesh.agent.AgentApplication from $AGENT_HOME/{conf,apps,lib}.
# The agent registers with the runtime over the control plane (/agent/*), subscribes
# its lite channel, and bridges routed prompts to an OpenAI-compatible LLM gateway.
#
# Env vars (all optional):
#   AGENT_RUNTIME_URL        runtime traffic URL              (default http://localhost:10105)
#   AGENT_ID                 agent identity                   (default agent-<ts>)
#   LLM_BASE_URL             OpenAI-compatible base URL       (default https://api.openai.com)
#   LLM_API_KEY              Bearer key; REQUIRED unless LLM_API_KEY_OPTIONAL=true
#   LLM_MODEL                model name                       (default gpt-4o-mini)
#   AGENT_HEARTBEAT_MS       heartbeat interval               (default 10000)
#   AGENT_HEARTBEAT_FAILLIMIT consecutive-failure exit limit  (default 6)
#   AGENT_MAX_HISTORY        per-conversation message window  (default 20)
#   AGENT_MAX_CONVERSATIONS  live conversation bound (LRU)    (default 1000)
#   AGENT_CAPACITY           advertised stream capacity       (default 100)
#   AGENT_OPTS               extra -D flags
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AGENT_HOME="${AGENT_HOME:-$(cd "$SCRIPT_DIR/.." && pwd)}"
cd "$AGENT_HOME"

AGENT_RUNTIME_URL="${AGENT_RUNTIME_URL:-http://localhost:10105}"
AGENT_ID="${AGENT_ID:-}"
LLM_BASE_URL="${LLM_BASE_URL:-https://api.openai.com}"
LLM_API_KEY="${LLM_API_KEY:-}"
LLM_MODEL="${LLM_MODEL:-gpt-4o-mini}"
AGENT_HEARTBEAT_MS="${AGENT_HEARTBEAT_MS:-10000}"
AGENT_HEARTBEAT_FAILLIMIT="${AGENT_HEARTBEAT_FAILLIMIT:-6}"
AGENT_MAX_HISTORY="${AGENT_MAX_HISTORY:-20}"
AGENT_MAX_CONVERSATIONS="${AGENT_MAX_CONVERSATIONS:-1000}"
AGENT_CAPACITY="${AGENT_CAPACITY:-100}"
AGENT_OPTS="${AGENT_OPTS:-}"

ARGS=""
if [ -n "$AGENT_ID" ]; then
    ARGS="$ARGS -Dagent.id=${AGENT_ID}"
fi

exec java     -Xmx512m     $AGENT_OPTS     -cp "conf:apps/*:lib/*"     -Dagent.runtime.url="${AGENT_RUNTIME_URL}"     -Dllm.base.url="${LLM_BASE_URL}"     -Dllm.api.key="${LLM_API_KEY}"     -Dllm.model="${LLM_MODEL}"     -Dagent.heartbeat.intervalMs="${AGENT_HEARTBEAT_MS}"     -Dagent.heartbeat.failLimit="${AGENT_HEARTBEAT_FAILLIMIT}"     -Dagent.conversation.maxHistory="${AGENT_MAX_HISTORY}"     -Dagent.conversation.maxConversations="${AGENT_MAX_CONVERSATIONS}"     -Dagent.capacity="${AGENT_CAPACITY}"     $ARGS     org.apache.eventmesh.agent.AgentApplication
