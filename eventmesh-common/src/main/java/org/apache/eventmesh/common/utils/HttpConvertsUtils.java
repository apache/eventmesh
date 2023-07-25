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

package org.apache.eventmesh.common.utils;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.header.Header;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpConvertsUtils {

    public Map<String, Object> httpMapConverts(Header header, ProtocolKey protocolKey) {
        Map<String, Object> map = new HashMap<>();
        Class<? extends Header> headerClass = header.getClass();
        Class<?> protocolKeyClass = protocolKey.getClass();
        Field[] headerFields = headerClass.getDeclaredFields();
        Field[] protocolKeyFields = protocolKeyClass.getDeclaredFields();
        for (Field headerField : headerFields) {
            try {
                final String headerFieldName = headerField.getName();
                final String headerFieldValue = headerField.get(header).toString();
                for (Field protocolKeyField : protocolKeyFields) {
                    final String protocolKeyValue = protocolKeyField.get(protocolKey).toString();
                    //
                    if (StringUtils.equalsIgnoreCase(headerFieldName, protocolKeyValue)) {
                        map.put(protocolKeyValue, headerFieldValue);
                    }
                }

                EnumSet<ProtocolKey.ClientInstanceKey> clientInstanceKeys = EnumSet.allOf(ProtocolKey.ClientInstanceKey.class);
                for (ProtocolKey.ClientInstanceKey clientInstanceKey : clientInstanceKeys) {
                    if (StringUtils.equalsIgnoreCase(headerFieldName, clientInstanceKey.getKey())) {
                        map.put(clientInstanceKey.getKey(), headerFieldValue);
                    }
                }
            } catch (IllegalAccessException e) {
                log.error("http map conversion failed.", e);
            }
        }
        return map;
    }

    public Map<String, Object> httpMapConverts(Header header, ProtocolKey protocolKey, ProtocolKey.EventMeshInstanceKey eventMeshInstanceKey) {
        Map<String, Object> map = new HashMap<>();
        Class<? extends Header> headerClass = header.getClass();
        Class<?> protocolKeyClass = protocolKey.getClass();
        Class<?> eventMeshInstanceKeyClass = eventMeshInstanceKey.getClass();
        Field[] headerFields = headerClass.getDeclaredFields();
        Field[] protocolKeyFields = protocolKeyClass.getDeclaredFields();
        Field[] eventMeshInstanceKeyFields = eventMeshInstanceKeyClass.getDeclaredFields();
        for (Field headerField : headerFields) {
            try {
                final String headerFieldName = headerField.getName();
                final String headerFieldValue = headerField.get(header).toString();
                for (Field protocolKeyField : protocolKeyFields) {
                    final String protocolKeyFieldValue = protocolKeyField.get(protocolKey).toString();
                    if (StringUtils.equalsIgnoreCase(headerFieldName, protocolKeyFieldValue)) {
                        map.put(protocolKeyFieldValue, headerFieldValue);
                    }
                }

                for (Field eventMeshInstanceKeyField : eventMeshInstanceKeyFields) {
                    final String eventMeshInstanceKeyValue = eventMeshInstanceKeyField.get(protocolKey).toString();
                    if (StringUtils.equalsIgnoreCase(headerFieldName, eventMeshInstanceKeyValue)) {
                        map.put(eventMeshInstanceKeyValue, headerFieldValue);
                    }
                }
            } catch (IllegalAccessException e) {
                log.error("http map conversion failed.", e);
            }
        }
        return map;
    }

    public Header httpHeaderConverts(Header header, Map<String, Object> headerParam) {
        Class<? extends Header> headerClass = header.getClass();
        ProtocolKey protocolKey = new ProtocolKey();
        Class<? extends ProtocolKey> protocolKeyClass = protocolKey.getClass();
        Field[] protocolKeyFields = protocolKeyClass.getDeclaredFields();
        Field[] headerFields = headerClass.getDeclaredFields();

        for (Field headerField : headerFields) {
            headerField.setAccessible(true);
            String headerFieldName = headerField.getName();
            try {
                for (Field protocolKeyField : protocolKeyFields) {
                    switch (headerFieldName) {
                        case ProtocolKey.PROTOCOL_VERSION:
                            headerField.set(header, ProtocolVersion.get(MapUtils.getString(headerParam, ProtocolKey.VERSION)));
                            break;
                        case ProtocolKey.LANGUAGE:
                            String language = StringUtils.isBlank(MapUtils.getString(headerParam, ProtocolKey.LANGUAGE))
                                ? Constants.LANGUAGE_JAVA : MapUtils.getString(headerParam, ProtocolKey.LANGUAGE);
                            headerField.set(header, language);
                            break;
                        default:
                            if (StringUtils.equalsIgnoreCase(headerFieldName, protocolKeyField.getName())) {
                                headerField.set(header, MapUtils.getString(headerParam, protocolKeyField.get(protocolKey).toString()));
                            }
                            break;
                    }
                }

                EnumSet<ProtocolKey.ClientInstanceKey> clientInstanceKeys = EnumSet.allOf(ProtocolKey.ClientInstanceKey.class);
                for (ProtocolKey.ClientInstanceKey clientInstanceKey : clientInstanceKeys) {
                    if (StringUtils.equalsIgnoreCase(headerFieldName, clientInstanceKey.getKey())) {
                        headerField.set(header, MapUtils.getString(headerParam, clientInstanceKey.getKey()));
                    }
                }
            } catch (IllegalAccessException e) {
                log.error("http header builder conversion failed.", e);
            }
        }
        return header;
    }
}
