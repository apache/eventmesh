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

package com.webank.eventmesh.connector.defibus.common;

public class ProxyConstants {

    public static final String EVENT_STORE_PROPERTIES = "eventstore";

    public static final String EVENT_STORE_ENV = "EVENT_STORE";

    public static final String PROTOCOL_HTTP = "http";

    public static final String PROTOCOL_TCP = "tcp";

    public static final String BROADCAST_PREFIX = "broadcast-";

    public final static String CONSUMER_GROUP_NAME_PREFIX = "ConsumerGroup-";

    public final static String PRODUCER_GROUP_NAME_PREFIX = "ProducerGroup-";

    public static final String DEFAULT_CHARSET = "UTF-8";

    public static final String PROXY_CONF_HOME = System.getProperty("confPath", System.getenv("confPath"));

    public static final String PROXY_CONF_FILE = "proxy.properties";

    public static final String REQ_C2PROXY_TIMESTAMP = "req_c2access_timestamp";
    public static final String REQ_PROXY2MQ_TIMESTAMP = "req_access2mq_timestamp";
    public static final String REQ_MQ2PROXY_TIMESTAMP = "req_mq2access_timestamp";
    public static final String REQ_PROXY2C_TIMESTAMP = "req_access2c_timestamp";
    public static final String RSP_C2PROXY_TIMESTAMP = "rsp_c2access_timestamp";
    public static final String RSP_PROXY2MQ_TIMESTAMP = "rsp_access2mq_timestamp";
    public static final String RSP_MQ2PROXY_TIMESTAMP = "rsp_mq2access_timestamp";
    public static final String RSP_PROXY2C_TIMESTAMP = "rsp_access2c_timestamp";

    public static final String REQ_SEND_PROXY_IP = "req_send_proxy_ip";
    public static final String REQ_RECEIVE_PROXY_IP = "req_receive_proxy_ip";
    public static final String RSP_SEND_PROXY_IP = "rsp_send_proxy_ip";
    public static final String RSP_RECEIVE_PROXY_IP = "rsp_receive_proxy_ip";

    //default TTL 4 hours
    public static final Integer DEFAULT_MSG_TTL_MILLS = 14400000;

    public static final int DEFAULT_TIMEOUT_IN_MILLISECONDS = 3000;

    public static final int DEFAULT_FASTFAIL_TIMEOUT_IN_MILLISECONDS = 100;

    public static final int DEFAULT_PUSH_RETRY_TIMES = 3;

    public static final int DEFAULT_PUSH_RETRY_TIME_DISTANCE_IN_MILLSECONDS = 3000;

    public static final String PURPOSE_PUB = "pub";

    public static final String PURPOSE_SUB = "sub";

    public static final String PURPOSE_ALL = "all";

    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";


    public static final String BORN_TIMESTAMP = "BORN_TIME";
    public static final String STORE_TIMESTAMP = "STORE_TIME";
    public static final String LEAVE_TIMESTAMP = "LEAVE_TIME";
    public static final String ARRIVE_TIMESTAMP = "ARRIVE_TIME";

    public static final String KEYS_UPPERCASE = "KEYS";
    public static final String KEYS_LOWERCASE = "keys";
    public static final String RR_REQUEST_UNIQ_ID = "RR_REQUEST_UNIQ_ID";
    public static final String TTL = "TTL";

    public static final String TAG = "TAG";

    public static final String MANAGE_DCN = "dcn";
    public static final String MANAGE_SUBSYSTEM = "subSystem";
    public static final String MANAGE_IP = "ip";
    public static final String MANAGE_PORT = "port";
    public static final String MANAGE_DEST_IP = "destProxyIp";
    public static final String MANAGE_DEST_PORT = "destProxyPort";
    public static final String MANAGE_PATH = "path";
    public static final String MANAGE_GROUP = "group";
    public static final String MANAGE_PURPOSE = "purpose";
    public static final String MANAGE_TOPIC = "topic";

    public static final String  PROXY_SEND_BACK_TIMES= "proxySendBackTimes";

    public static final String  PROXY_SEND_BACK_IP= "proxySendBackIp";

    public static final String  PROXY_REGISTRY_ADDR_KEY= "proxyRegistryAddr";

    public static int DEFAULT_TIME_OUT_MILLS = 5 * 1000;

}
