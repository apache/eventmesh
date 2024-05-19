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

package org.apache.eventmesh.trace.pinpoint.config;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.common.config.ConfigField;
import org.apache.eventmesh.common.exception.JsonException;
import org.apache.eventmesh.common.utils.RandomStringUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.Properties;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navercorp.pinpoint.profiler.context.grpc.config.GrpcTransportConfig;

import lombok.Data;

@Data
@Config(prefix = "eventmesh.trace.pinpoint", path = "classPath://pinpoint.properties")
public final class PinpointConfiguration {

    @ConfigField(field = "agentId", reload = true)
    private String agentId;

    @ConfigField(field = "agentName", reload = true)
    private String agentName;

    @ConfigField(field = "applicationName", findEnv = true, notNull = true)
    private String applicationName;

    @ConfigField(field = "", reload = true)
    private Properties grpcTransportProperties;

    private GrpcTransportConfig grpcTransportConfig;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public void reload() {
        if (StringUtils.isBlank(agentName)) {
            agentName = applicationName;
        }

        if (StringUtils.isBlank(agentId)) {
            // refer to: com.navercorp.pinpoint.common.util.IdValidateUtils#validateId
            agentId = StringUtils.substring(agentName, 0, 15)
                + Constants.HYPHEN
                + RandomStringUtils.generateNum(8);
        }

        // Map to Pinpoint property configuration.
        grpcTransportConfig = convertValue(grpcTransportProperties, GrpcTransportConfig.class);
    }

    public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
        try {
            return OBJECT_MAPPER.convertValue(fromValue, toValueType);
        } catch (IllegalArgumentException e) {
            throw new JsonException("convertValue fromValue to toValueType instance error", e);
        }
    }
}
