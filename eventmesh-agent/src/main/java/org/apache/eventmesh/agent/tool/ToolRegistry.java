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

import org.apache.eventmesh.agent.llm.ToolSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Named, ordered set of {@link AgentTool}s; renders the tool list for the LLM tool loop. */
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public ToolRegistry register(AgentTool tool) {
        tools.put(tool.name(), tool);
        return this;
    }

    public boolean isEmpty() {
        return tools.isEmpty();
    }

    public int size() {
        return tools.size();
    }

    /** Run one tool by name; unknown names throw IllegalArgumentException. */
    public String invoke(String name, Map<String, Object> args) throws Exception {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("unknown tool: " + name);
        }
        return tool.invoke(args);
    }

    /** Render the registered tools as LLM tool advertisements (OpenAI function-calling shape). */
    public List<ToolSpec> specs() {
        List<ToolSpec> specs = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            specs.add(new ToolSpec(tool.name(), tool.description(), tool.parametersJsonSchema()));
        }
        return specs;
    }
}
