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

package org.apache.eventmesh.runtime.client.common;

import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.TimeZone;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

<<<<<<<< HEAD:eventmesh-runtime/src/test/java/org/apache/eventmesh/runtime/client/common/ClientGlobal.java
========
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

>>>>>>>> e4cff57da85093ca7a917f7edd86fa434000d5dc:eventmesh-runtime/src/test/java/client/common/ClientGlobal.java

public class ClientGlobal {

    private static Logger logger = LoggerFactory.getLogger(ClientGlobal.class);

    public static ClientGlobal INSTANCE = new ClientGlobal();

    public static ClientGlobal getInstance() {
        return INSTANCE;
    }

    public static ObjectMapper jsonMapper;

    private ClientGlobal() {
        init();
    }

    public void init() {
        ObjectMapper jsonMapper = new ObjectMapper();
        jsonMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        jsonMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        jsonMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        jsonMapper.setTimeZone(TimeZone.getDefault());
        this.jsonMapper = jsonMapper;
        logger.info("ClientGlobal init success");
    }
}
