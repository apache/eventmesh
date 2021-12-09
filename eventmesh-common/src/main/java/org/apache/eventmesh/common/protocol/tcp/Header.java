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

package org.apache.eventmesh.common.protocol.tcp;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

@Data
public class Header {

    private Command             cmd;
    private int                 code;
    private String              desc;
    private String              seq;
    private Map<String, Object> properties = new HashMap<>();

    public Header() {
    }

    public Header(Command cmd, int code, String desc, String seq) {
        this.cmd = cmd;
        this.code = code;
        this.desc = desc;
        this.seq = seq;
    }

    public Header(int code, String desc, String seq, Map<String, Object> properties) {
        this.code = code;
        this.desc = desc;
        this.seq = seq;
        this.properties = properties;
    }


    public void putProperty(final String name, final Object value) {
        if (null == this.properties) {
            this.properties = new HashMap<>();
        }

        this.properties.put(name, value);
    }

    public Object getProperty(final String name) {
        if (null == this.properties) {
            return null;
        }
        return this.properties.get(name);
    }

    public String getStringProperty(final String name) {
        Object property = getProperty(name);
        if (null == property) {
            return null;
        }
        return property.toString();
    }

}
