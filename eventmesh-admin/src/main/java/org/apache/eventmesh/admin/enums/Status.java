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

import static org.apache.eventmesh.admin.constant.ConfigConst.COLON;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * An enumeration class that conforms to the RESTful specifications and custom error reporting requirements.
 * <ul>
 *   <li>The 'code' field is used to return the HTTP status code using {@link HttpStatus}.</li>
 *   <li>The 'category' field represents the major category of the error.</li>
 *   <li>the 'desc' field represents the detailed subcategory and information of the error.</li>
 * </ul>
 */

@Getter
public enum Status {

    SUCCESS(HttpStatus.OK, Category.SUCCESS, "Operation success."),

    NACOS_SDK_CONFIG_ERR(HttpStatus.INTERNAL_SERVER_ERROR, Category.SDK_CONFIG_ERR,
        "Failed to create Nacos ConfigService. Please check EventMeshAdmin application configuration."),

    NACOS_GET_CONFIGS_ERR(HttpStatus.BAD_GATEWAY, Category.META_COM_ERR, "Failed to retrieve Nacos config(s)."),

    NACOS_EMPTY_RESP_ERR(HttpStatus.BAD_GATEWAY, Category.META_COM_ERR, "No result returned by Nacos. Please check Nacos."),

    NACOS_LOGIN_ERR(HttpStatus.UNAUTHORIZED, Category.META_COM_ERR, "Nacos login failed."),

    NACOS_LOGIN_EMPTY_RESP_ERR(HttpStatus.BAD_GATEWAY, Category.META_COM_ERR, "Nacos didn't return accessToken. Please check Nacos status."),
    ;

    // error code
    private final HttpStatus code;

    // error type
    private final Category category;

    // error message
    private final String desc;

    Status(HttpStatus code, Category category, String desc) {
        this.code = code;
        this.category = category;
        this.desc = desc;
    }

    @Override
    public String toString() {
        return name() + " of " + category + COLON + desc;
    }

    @Getter
    public enum Category {

        SUCCESS("Successfully received and processed"),

        SDK_CONFIG_ERR("The Meta SDK config in EventMeshAdmin application.yml error"),

        META_COM_ERR("Network communication to Meta error"),
        ;

        /**
         * Helpful for understanding and not used for now
         */
        private final String desc;

        Category(String desc) {
            this.desc = desc;
        }
    }
}
