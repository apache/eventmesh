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

package org.apache.eventmesh.registry;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
public class RegisterServerInfo {

    // different implementations will have different formats
    @Getter
    @Setter
    private String serviceName;

    @Getter
    @Setter
    private String address;

    @Getter
    @Setter
    private boolean health;
    @Getter
    private Map<String, String> metadata = new HashMap<>();
    @Getter
    private Map<String, Object> extFields = new HashMap<>();

    public void setMetadata(Map<String, String> metadata) {
        if (metadata == null) {
            this.metadata.clear();
            return;
        }

        this.metadata = metadata;
    }

    public void addMetadata(String key, String value) {
        this.metadata.put(key, value);
    }

    public void setExtFields(Map<String, Object> extFields) {
        if (extFields == null) {
            this.extFields.clear();
            return;
        }

        this.extFields = extFields;
    }

    public void addExtFields(String key, Object value) {
        this.extFields.put(key, value);
    }
}
