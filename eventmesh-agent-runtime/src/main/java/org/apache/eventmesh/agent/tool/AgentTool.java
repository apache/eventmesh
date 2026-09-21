/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.agent.tool;

import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

import java.util.Map;

/**
 * An invocable capability the LLM may call via function calling during a
 * {@code StreamingAgent} tool loop. Register instances on a {@link ToolRegistry} and pass the
 * registry to the agent; the agent advertises every tool to the model, executes the calls the
 * model requests, and feeds results back until a final answer.
 *
 * <p><b>SPI deployment</b> (same mechanism as storage/connector plugins): annotate nothing extra —
 * implementations register themselves via a {@code META-INF/eventmesh/&lt;this-interface-FQCN&gt;}
 * service file ({@code <name>=<impl FQCN>}) inside their jar. Drop the jar into the agent's
 * {@code plugin/agent/} directory and reference it by name with
 * {@code -Dagent.tools.spi=<name>}; {@link ToolRegistry#registerSpi(String)} resolves it through
 * {@code EventMeshExtensionFactory}.</p>
 */
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.AGENT_TOOL)
public interface AgentTool {

    /** Stable tool name the model addresses this tool by. */
    String name();

    /** One-sentence description telling the model when to use this tool. */
    String description();

    /** JSON schema (type "object") describing the arguments object; may be permissive. */
    String parametersJsonSchema();

    /** Invoke with the model-produced arguments object; return a text result for the model. */
    String invoke(Map<String, Object> args) throws Exception;
}
