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

package org.apache.eventmesh.admin.common;

public class Constants {

    // config
    public static final String ADMIN_PROPS_PREFIX = "eventmesh";
    public static final String META_TYPE_NACOS = "nacos";
    public static final String META_TYPE_ETCD = "etcd";

    // Open-API
    public static final String HTTP_PREFIX = "http://";
    public static final String HTTPS_PREFIX = "https://";

    // Nacos
    public static final String NACOS_LOGIN_API = "/nacos/v1/auth/login";
    public static final String NACOS_LOGIN_REQ_USERNAME = "username";
    public static final String NACOS_LOGIN_REQ_PASSWORD = "password";
    public static final String NACOS_LOGIN_RESP_TOKEN = "accessToken";
    public static final String NACOS_CONFIGS_API = "/nacos/v1/cs/configs";
    public static final String NACOS_CONFIGS_REQ_PAGE = "pageNo";
    public static final String NACOS_CONFIGS_REQ_PAGE_SIZE = "pageSize";
    public static final String NACOS_CONFIGS_REQ_SEARCH = "pageSize";
    public static final String NACOS_CONFIGS_RESP_CONTENT_LIST = "pageItems"; // json page data list field
    public static final String NACOS_CONFIGS_RESP_CONTENT = "content"; // json page data field
    public static final String NACOS_CONFIGS_RESP_PAGES = "pagesAvailable"; // json total pages field
    public static final String NACOS_CONFIGS_RESP_DATAID = "dataId";
    public static final String NACOS_CONFIGS_RESP_GROUP = "group";
}
