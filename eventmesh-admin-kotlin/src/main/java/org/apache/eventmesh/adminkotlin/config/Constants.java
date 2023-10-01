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

package org.apache.eventmesh.adminkotlin.config;

public class Constants {

    // config
    public static final String ADMIN_PROPS_PREFIX = "eventmesh";
    public static final String META_TYPE_NACOS = "nacos";
    public static final String META_TYPE_ETCD = "etcd";

    // Open-API
    public static final String HTTP_PREFIX = "http://";
    public static final String HTTPS_PREFIX = "https://";
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    // Nacos
    public static final String NACOS_LOGIN_API = "/nacos/v1/auth/login";
    public static final String NACOS_CONFIGS_API = "/nacos/v1/cs/configs";
}
