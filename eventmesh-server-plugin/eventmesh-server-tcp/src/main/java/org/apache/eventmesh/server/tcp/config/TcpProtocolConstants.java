/*
 * Licensed to Apache Software Foundation (ASF) under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Apache Software Foundation (ASF) licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.eventmesh.server.tcp.config;

public enum TcpProtocolConstants {
    ;

    public static final String IP_PORT_SEPARATOR = ":";

    public static final String PROTOCOL_TCP = "tcp";

    public static final String TCP_CONFIGURATION_FILE = "eventmesh-server-tcp.yml";

    public static final String KEYS_UPPERCASE = "KEYS";
    public static final String KEYS_LOWERCASE = "keys";

    public static final int DEFAULT_TIMEOUT_IN_MILLISECONDS = 3000;

    public static final String TTL = "TTL";

    public static final String PROPERTY_MESSAGE_TTL = "TTL";

    public static final String EVENTMESH_SEND_BACK_TIMES = "eventMeshSendBackTimes";

    public static final String EVENTMESH_SEND_BACK_IP = "eventMeshSendBackIp";

    public static final String PURPOSE_PUB = "pub";
    public static final String PURPOSE_SUB = "sub";

    public static final String MANAGE_SUBSYSTEM = "subSystem";
    public static final String MANAGE_IP = "ip";
    public static final String MANAGE_PORT = "port";
    public static final String MANAGE_DEST_IP = "desteventMeshIp";
    public static final String MANAGE_DEST_PORT = "desteventMeshPort";
    public static final String MANAGE_PATH = "path";

    public static final String MANAGE_TOPIC = "topic";

    public static final String RR_REPLY_TOPIC = "rr-reply-topic";

    public static final String PROPERTY_MESSAGE_CLUSTER = "CLUSTER";

    public static final String REQ_C2EVENTMESH_TIMESTAMP = "req_c2eventMesh_timestamp";
    public static final String REQ_EVENTMESH2MQ_TIMESTAMP = "req_eventMesh2mq_timestamp";
    public static final String REQ_MQ2EVENTMESH_TIMESTAMP = "req_mq2eventMesh_timestamp";
    public static final String REQ_EVENTMESH2C_TIMESTAMP = "req_eventMesh2c_timestamp";
    public static final String RSP_C2EVENTMESH_TIMESTAMP = "rsp_c2eventMesh_timestamp";
    public static final String RSP_EVENTMESH2MQ_TIMESTAMP = "rsp_eventMesh2mq_timestamp";
    public static final String RSP_MQ2EVENTMESH_TIMESTAMP = "rsp_mq2eventMesh_timestamp";
    public static final String RSP_EVENTMESH2C_TIMESTAMP = "rsp_eventMesh2c_timestamp";

    public static final String REQ_SEND_EVENTMESH_IP = "req_send_eventMesh_ip";
    public static final String REQ_RECEIVE_EVENTMESH_IP = "req_receive_eventMesh_ip";
    public static final String RSP_SEND_EVENTMESH_IP = "rsp_send_eventMesh_ip";
    public static final String RSP_RECEIVE_EVENTMESH_IP = "rsp_receive_eventMesh_ip";

    public static final String PROPERTY_MESSAGE_KEYS = "KEYS";

    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

    public static final String RR_REQUEST_UNIQ_ID = "RR_REQUEST_UNIQ_ID";

    public static final String LEAVE_TIME = "LEAVE_TIME";            //leaveBrokerTime
    public static final String ARRIVE_TIME = "ARRIVE_TIME";
    public static final String STORE_TIME = "STORE_TIME";

    public static final String MANAGE_GROUP = "group";
    public static final String MANAGE_PURPOSE = "purpose";
}
