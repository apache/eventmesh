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

package org.apache.eventmesh.admin.enums;

import lombok.Getter;

@Getter
public enum ErrorType {

    SUCCESS("SUCCESS", "success"),

    NACOS_SDK_CONFIG_ERR("SDK_CONFIG_ERR", "Failed to create Nacos ConfigService. Please check EventMeshAdmin application configuration."),

    NACOS_GET_CONFIGS_ERR("META_COM_ERR", "Failed to retrieve Nacos config(s)."),

    NACOS_EMPTY_RESP_ERR("META_COM_ERR", "No result returned by Nacos. Please check Nacos."),

    NACOS_LOGIN_ERR("META_COM_ERR", "Nacos login failed."),

    NACOS_LOGIN_EMPTY_RESP_ERR("META_COM_ERR", "Nacos didn't return accessToken. Please check Nacos status."),
    ;

    // error type
    private final String type;

    // error message
    private final String desc;

    ErrorType(String type, String desc) {
        this.type = type;
        this.desc = desc;
    }
}
