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

public class Constants {

    public static final String DEFAULT_CHARSET = "UTF-8";

    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

    public static final String LANGUAGE_JAVA = "JAVA";

    public static final String HTTP_PROTOCOL_PREFIX = "http://";

    public static final String HTTPS_PROTOCOL_PREFIX = "https://";

    public static final String PROTOCOL_TYPE = "protocoltype";

    public static final String PROTOCOL_VERSION = "protocolversion";

    public static final String PROTOCOL_DESC = "protocoldesc";

    public static final int DEFAULT_HTTP_TIME_OUT = 15000;

    public static final String EVENTMESH_MESSAGE_CONST_TTL = "ttl";

    public static final String DEFAULT_EVENTMESH_MESSAGE_TTL = "4000";

    public static final Integer DEFAULT_CLIENT_UNACK = 12;

    public static final String CONSTANTS_SERVICE_DESC_ENV = "env";

    public static final String CONSTANTS_SERVICE_DESC_VERSION = "version";

    public static final String CONSTANTS_INSTANCE_DESC_ENV = "env";

    public static final String CONSTANTS_INSTANCE_DESC_IDC = "idc";

    public static final String CONSTANTS_INSTANCE_DESC_SYSID = "sysId";

    public static final String CONSTANTS_INSTANCE_DESC_IP = "ip";

    public static final String CONSTANTS_INSTANCE_DESC_PORT = "port";

    public static final String KEY_CONSTANTS_INSTANCE_DESC_PID = "pid";

    public static final String RMB_UNIQ_ID = "rmbuniqid";

    public static final String IDC_SEPERATER = "-";

    public static final String PROPERTY_MESSAGE_TIMEOUT = "timeout";

    public static final String PROPERTY_MESSAGE_SEARCH_KEYS = "searchkeys";

    public static final String PROPERTY_MESSAGE_QUEUE_ID = "queueid";

    public static final String PROPERTY_MESSAGE_QUEUE_OFFSET = "queueoffset";

    public static final String PROPERTY_MESSAGE_DESTINATION = "destination";

    public static final String PROPERTY_MESSAGE_MESSAGE_ID = "messageid";

    public static final String PROPERTY_MESSAGE_BORN_HOST = "bornhost";

    public static final String PROPERTY_MESSAGE_BORN_TIMESTAMP = "borntimestamp";

    public static final String PROPERTY_MESSAGE_STORE_HOST = "storehost";

    public static final String PROPERTY_MESSAGE_STORE_TIMESTAMP = "storetimestamp";

    public static final String MESSAGE_PROP_SEPARATOR = "99";

    public static final String EVENTMESH_CONF_HOME = System.getProperty("confPath", System.getenv("confPath"));

}
