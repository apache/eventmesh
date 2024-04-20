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

package org.apache.eventmesh.runtime.admin.handler.v2;

import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.common.config.ConfigField;
import org.apache.eventmesh.runtime.admin.handler.AbstractHttpHandler;
import org.apache.eventmesh.runtime.admin.response.Result;
import org.apache.eventmesh.runtime.admin.response.v2.GetConfigurationResponse;
import org.apache.eventmesh.runtime.common.EventMeshHttpHandler;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.util.HttpRequestUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.filter.Filter;
import com.alibaba.fastjson2.filter.NameFilter;
import com.alibaba.fastjson2.filter.ValueFilter;

import lombok.extern.slf4j.Slf4j;

import inet.ipaddr.IPAddress;

/**
 * This class handles the {@code /v2/configuration} endpoint, corresponding to the {@code eventmesh-dashboard} path {@code /}.
 * <p>
 * This handler is responsible for retrieving the current configuration information of the EventMesh node, including service name, service
 * environment, and listening ports for various protocols.
 */

@Slf4j
@EventMeshHttpHandler(path = "/v2/configuration")
public class ConfigurationHandler extends AbstractHttpHandler {

    private final EventMeshTCPConfiguration eventMeshTCPConfiguration;
    private final EventMeshHTTPConfiguration eventMeshHTTPConfiguration;
    private final EventMeshGrpcConfiguration eventMeshGrpcConfiguration;

    /**
     * Constructs a new instance with the provided configurations.
     *
     * @param eventMeshTCPConfiguration  the TCP configuration for EventMesh
     * @param eventMeshHTTPConfiguration the HTTP configuration for EventMesh
     * @param eventMeshGrpcConfiguration the gRPC configuration for EventMesh
     */
    public ConfigurationHandler(
        EventMeshTCPConfiguration eventMeshTCPConfiguration,
        EventMeshHTTPConfiguration eventMeshHTTPConfiguration,
        EventMeshGrpcConfiguration eventMeshGrpcConfiguration) {
        super();
        this.eventMeshTCPConfiguration = eventMeshTCPConfiguration;
        this.eventMeshHTTPConfiguration = eventMeshHTTPConfiguration;
        this.eventMeshGrpcConfiguration = eventMeshGrpcConfiguration;
    }

    /**
     * Parameters:
     * <ul>
     *     <li>
     *         {@code format}: String; Optional, DefaultValue: {@code properties}, SelectableValue: {@code bean}.
     *         <p>When {@code properties}, the field names are returned in Properties format;
     *         <p>When {@code bean}, the field names themselves are used as json keys.
     *     </li>
     * </ul>
     */
    @Override
    protected void get(HttpRequest httpRequest, ChannelHandlerContext ctx) {
        String format = HttpRequestUtil.getQueryParam(httpRequest, "format", "properties");

        Filter[] filters;
        if (format.equals("properties")) {
            filters = new Filter[] {new ConfigFieldFilter(), new IPAddressToStringFilter()};
        } else if (format.equals("bean")) {
            filters = new Filter[] {new IPAddressToStringFilter()};
        } else {
            log.warn("Invalid format param: {}", format);
            writeBadRequest(ctx, "Invalid format param: " + format);
            return;
        }

        GetConfigurationResponse getConfigurationResponse = new GetConfigurationResponse(
            eventMeshTCPConfiguration,
            eventMeshHTTPConfiguration,
            eventMeshGrpcConfiguration
        );
        String json = JSON.toJSONString(Result.success(getConfigurationResponse), filters);
        writeJson(ctx, json);
    }

    /**
     * For each member of {@link EventMeshTCPConfiguration}, {@link EventMeshHTTPConfiguration}, and {@link EventMeshGrpcConfiguration},
     * the value of the {@link ConfigField} annotation for each field is obtained through reflection,
     * and then concatenated with the configuration prefix in the {@link Config} annotation to serve as the JSON key for this field.
     * <p>
     * When the {@code name} is a member that only exists in the superclass, it will be searched for in the {@link CommonConfiguration} class.
     * <p>
     * If a field does not have a {@link ConfigField} annotation or the value of the {@link ConfigField} annotation is empty,
     * this field will be added to the JSON with the field name as the key, rather than in properties format.
     */
    static class ConfigFieldFilter implements NameFilter {
        @Override
        public String process(Object object, String name, Object value) {
            try {
                Field field = findFieldInClassHierarchy(object.getClass(), name);
                if (field != null && field.isAnnotationPresent(ConfigField.class)) {
                    ConfigField configField = field.getAnnotation(ConfigField.class);
                    String fieldAnnotationValue = configField.field();
                    if (!fieldAnnotationValue.isEmpty()) {
                        Config config = object.getClass().getAnnotation(Config.class);
                        String prefix = config.prefix();
                        return prefix + "." + fieldAnnotationValue;
                    }
                }
            } catch (NoSuchFieldException e) {
                log.error("Failed to get field {} from object {}", name, object, e);
            }
            return name;
        }

        private Field findFieldInClassHierarchy(Class<?> clazz, String fieldName) throws NoSuchFieldException {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                Class<?> superclass = clazz.getSuperclass();
                if (superclass == null) {
                    throw e;
                } else {
                    return findFieldInClassHierarchy(superclass, fieldName);
                }
            }
        }
    }

    /**
     * {@link IPAddress} can't be serialized directly by FastJSON,
     * so this filter converts {@link IPAddress} objects to their string representation.
     */
    static class IPAddressToStringFilter implements ValueFilter {
        @Override
        public Object apply(Object object, String name, Object value) {
            if (name.equals("eventMeshIpv4BlackList") || name.equals("eventMeshIpv6BlackList")) {
                if (value instanceof List) {
                    List<String> ipList = new ArrayList<>();
                    for (Object o : (List<?>) value) {
                        if (o instanceof IPAddress) {
                            ipList.add(((IPAddress) o).toNormalizedString());
                        }
                    }
                    return ipList;
                }
            }
            return value;
        }
    }
}
