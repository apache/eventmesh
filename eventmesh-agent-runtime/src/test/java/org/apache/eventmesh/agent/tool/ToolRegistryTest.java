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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** Covers registry lookup, spec rendering and the unknown-tool error path. */
class ToolRegistryTest {

    private static AgentTool fixedTool(String name, String result) {
        return new AgentTool() {

            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "test tool " + name;
            }

            @Override
            public String parametersJsonSchema() {
                return "{\"type\":\"object\"}";
            }

            @Override
            public String invoke(Map<String, Object> args) {
                return result;
            }
        };
    }

    @Test
    void invokesByName() throws Exception {
        ToolRegistry registry = new ToolRegistry().register(fixedTool("echo", "ok"));
        assertThat(registry.invoke("echo", Map.of())).isEqualTo("ok");
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.isEmpty()).isFalse();
    }

    @Test
    void unknownToolThrows() {
        ToolRegistry registry = new ToolRegistry();
        assertThatThrownBy(() -> registry.invoke("nope", Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown tool");
    }

    @Test
    void specsRenderNameDescriptionSchema() {
        ToolRegistry registry = new ToolRegistry().register(fixedTool("a", "x")).register(fixedTool("b", "y"));
        assertThat(registry.specs()).hasSize(2);
        assertThat(registry.specs().get(0).name()).isEqualTo("a");
        assertThat(registry.specs().get(1).description()).isEqualTo("test tool b");
        assertThat(registry.specs().get(0).parametersJsonSchema()).isEqualTo("{\"type\":\"object\"}");
    }
}
