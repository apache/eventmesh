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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Constants {

    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    public static final String DATE_FORMAT_INCLUDE_MILLISECONDS = "yyyy-MM-dd HH:mm:ss.SSS";

    public static final String DATE_FORMAT_DEFAULT = "yyyy-MM-dd HH:mm:ss";

    public static final String DATA_CONTENT_TYPE = "datacontenttype";

    public static final String LANGUAGE_JAVA = "JAVA";

    public static final String CONNECT_SERVER_CONFIG_FILE_NAME = "server-config.yml";

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

    public static final String PRODUCER_ID = "PRODUCER_ID";

    public static final String CONSUMER_ID = "CONSUMER_ID";

    public static final String BROADCAST_PREFIX = "broadcast-";

    public static final String IS_BROADCAST = "isBroadcast";

    public static final String CONSUMER_GROUP = "consumerGroup";

    public static final String PRODUCER_GROUP = "producerGroup";

    public static final String PRODUCER_TOKEN = "producerToken";

    public static final String CONSUMER_TOKEN = "consumerToken";

    public static final String INSTANCE_NAME = "instanceName";

    public static final String ACCESS_POINTS = "ACCESS_POINTS";

    public static final String CLIENT_ADDRESS = "clientAddress";

    public static final String REGION = "REGION";

    public static final String MESSAGE_MODEL = "MESSAGE_MODEL";

    public static final String NAMESPACE = "namespace";

    public static final String RMQ_PRODUCER_GROUP = "RMQ_PRODUCER_GROUP";

    public static final String OPERATION_TIMEOUT = "OPERATION_TIMEOUT";

    public static final String CLOUD_EVENTS_PROTOCOL_NAME = "cloudevents";

    public static final String EM_MESSAGE_PROTOCOL_NAME = "eventmeshmessage";

    public static final String OPEN_MESSAGE_PROTOCOL_NAME = "openmessage";

    // delimiter define
    public static final String COMMA = ",";

    public static final String VERTICAL_LINE = "|";

    public static final String COLON = ":";

    public static final String HYPHEN = "-";

    public static final String DOT = ".";

    public static final String POUND = "#";

    public static final String ASTERISK = "*";

    public static final String UNDER_LINE = "_";

    public static final String UNKNOWN = "unknown";

    public static final String LEFT_PARENTHESIS = "(";

    public static final String RIGHT_PARENTHESIS = ")";

    public static final String LINE_BREAK = "\n";

    public static final String TAB = "\t";

    public static final String AT = "@";

    public static final String QUESTION_MARK = "?";

    public static final String AND = "&";

    public static final String EQ = "=";

    public static final String EMPTY = "";

    public static final int SUCCESS_CODE = 200;

    public static final String SINK = "Sink";

    public static final String SOURCE = "Source";

    // protocol desc
    public static final String PROTOCOL_DESC_GRPC_CLOUD_EVENT = "grpc-cloud-event";

    public static final String PROTOCOL_DESC_HTTP = "http";

    public static final String PROTOCOL_DESC_TCP = "tcp";

    /**
     * GRPC PROTOCOL
     */
    public static final String PROTOCOL_GRPC = "grpc";

    /**
     * application/cloudevents+json Content-type
     */
    public static final String CONTENT_TYPE_CLOUDEVENTS_JSON = "application/cloudevents+json";

    public static final String HTTP = "HTTP";

    public static final String TCP = "TCP";

    public static final String GRPC = "GRPC";

    public static final String OS_NAME_KEY = "os.name";

    public static final String OS_WIN_PREFIX = "win";

    public static final String DEFAULT = "default";

    public static final String ADMIN_SERVER_REGISTRY_NAME = "DEFAULT_GROUP@@em_adm_server";
}
