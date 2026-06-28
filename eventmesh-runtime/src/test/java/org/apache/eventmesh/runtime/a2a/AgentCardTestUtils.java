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

package org.apache.eventmesh.runtime.a2a;

import org.apache.eventmesh.protocol.a2a.model.AgentCapabilities;
import org.apache.eventmesh.protocol.a2a.model.AgentCard;
import org.apache.eventmesh.protocol.a2a.model.AgentInterface;
import org.apache.eventmesh.protocol.a2a.model.AgentSkill;

import java.util.Arrays;
import java.util.Collections;

/**
 * Test utility for creating valid AgentCard instances that pass schema validation.
 */
public final class AgentCardTestUtils {

    private AgentCardTestUtils() {
    }

    public static AgentCard createValidCard(String name) {
        return AgentCard.builder()
            .name(name)
            .description("Test agent: " + name)
            .version("1.0.0")
            .supportedInterfaces(Arrays.asList(
                AgentInterface.builder()
                    .url("http://localhost:10105/a2a")
                    .protocolBinding("JSONRPC")
                    .protocolVersion("0.3")
                    .build()
            ))
            .capabilities(AgentCapabilities.builder()
                .streaming(false)
                .pushNotifications(false)
                .build())
            .defaultInputModes(Arrays.asList("text/plain"))
            .defaultOutputModes(Arrays.asList("text/plain"))
            .skills(Arrays.asList(
                AgentSkill.builder()
                    .id("test-skill")
                    .name("Test Skill")
                    .description("A test skill")
                    .tags(Collections.singletonList("test"))
                    .build()
            ))
            .build();
    }
}
