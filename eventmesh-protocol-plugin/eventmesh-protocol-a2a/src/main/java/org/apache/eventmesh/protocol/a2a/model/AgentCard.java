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

package org.apache.eventmesh.protocol.a2a.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an A2A Agent Card as defined by the A2A protocol specification.
 * Reference: https://a2a-protocol.org/latest/specification/#agent-card
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentCard implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;

    private String description;

    private String version;

    @JsonProperty("supportedInterfaces")
    private List<AgentInterface> supportedInterfaces;

    private AgentProvider provider;

    private AgentCapabilities capabilities;

    private List<AgentSkill> skills;

    private List<AgentCardSignature> signatures;

    @JsonProperty("securitySchemes")
    private Map<String, SecurityScheme> securitySchemes;

    @JsonProperty("securityRequirements")
    private List<SecurityRequirement> securityRequirements;

    @JsonProperty("defaultInputModes")
    private List<String> defaultInputModes;

    @JsonProperty("defaultOutputModes")
    private List<String> defaultOutputModes;

    @JsonProperty("documentationUrl")
    private String documentationUrl;

    @JsonProperty("iconUrl")
    private String iconUrl;
}
