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

package org.apache.eventmesh.common;

import java.util.HashMap;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * EventMesh message.
 */
@Builder
@Data
public class EventMeshMessage {

    private String bizSeqNo;

    private String uniqueId;

    private String topic;

    private String content;

    private Map<String, String> prop;

    @Builder.Default
    private final long createTime = System.currentTimeMillis();

    public EventMeshMessage addProp(String key, String val) {
        if (prop == null) {
            prop = new HashMap<>();
        }
        prop.put(key, val);
        return this;
    }

    public String getProp(String key) {
        if (prop == null) {
            return null;
        }
        return prop.get(key);
    }

    public EventMeshMessage removePropIfPresent(String key) {
        if (prop == null) {

            return this;
        }
        prop.remove(key);
        return this;
    }

}
