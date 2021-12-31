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

package org.apache.eventmesh.common.protocol.http.header;

import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;

import org.apache.commons.collections4.MapUtils;

import java.util.HashMap;
import java.util.Map;

public class BaseRequestHeader extends Header {
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public static BaseRequestHeader buildHeader(Map<String, Object> headerParam) {
        BaseRequestHeader header = new BaseRequestHeader();
        header.setCode(MapUtils.getString(headerParam, ProtocolKey.REQUEST_CODE));
        return header;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(ProtocolKey.REQUEST_CODE, code);
        return map;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("baseRequestHeader={code=")
                .append(code).append("}");
        return sb.toString();
    }
}
