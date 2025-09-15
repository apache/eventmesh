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

package org.apache.eventmesh.connector.mcp.source.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter.Feature;
import java.util.Map;

/**
 * Mcp response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpResponse implements Serializable {
    private static final long serialVersionUID = 8616938575207104455L;

    private String msg;

    private LocalDateTime handleTime;

    /**
     * Convert to json string.
     *
     * @return json string
     */
    public String toJsonStr() {
        return JSON.toJSONString(this, Feature.WriteMapNullValue);
    }

    /**
     * Create a success response.
     *
     * @return response
     */
    public static McpResponse success() {
        return base("success");
    }


    /**
     * Create a base response.
     *
     * @param msg message
     * @return response
     */
    public static McpResponse base(String msg) {
        return new McpResponse(msg, LocalDateTime.now());
    }
}
