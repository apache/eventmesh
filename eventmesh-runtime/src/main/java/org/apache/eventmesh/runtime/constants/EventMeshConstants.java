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

package org.apache.eventmesh.runtime.constants;

import org.apache.eventmesh.common.Constants;

public class EventMeshConstants {

    public static final String EVENT_STORE_PROPERTIES = "eventstore";

    public static final String EVENT_STORE_ENV = "EVENT_STORE";

    public static final String PROTOCOL_HTTP = "http";

    public static final String PROTOCOL_TCP = "tcp";

    public static final String PROTOCOL_GRPC = "grpc";

    public static final String DEFAULT_CHARSET = "UTF-8";

    public static final String IP_PORT_SEPARATOR = ":";

    public static final String EVENTMESH_CONF_HOME = System.getProperty("confPath", System.getenv("confPath"));

    public static final String EVENTMESH_CONF_FILE = "eventmesh.properties";

    public static final String REQ_C2EVENTMESH_TIMESTAMP = "reqc2eventmeshtimestamp";
    public static final String REQ_EVENTMESH2MQ_TIMESTAMP = "reqeventmesh2mqtimestamp";
    public static final String REQ_MQ2EVENTMESH_TIMESTAMP = "reqmq2eventmeshtimestamp";
    public static final String REQ_EVENTMESH2C_TIMESTAMP = "reqeventmesh2ctimestamp";
    public static final String RSP_C2EVENTMESH_TIMESTAMP = "rspc2eventmeshtimestamp";
    public static final String RSP_EVENTMESH2MQ_TIMESTAMP = "rspeventmesh2mqtimestamp";
    public static final String RSP_MQ2EVENTMESH_TIMESTAMP = "rspmq2eventmeshtimestamp";
    public static final String RSP_EVENTMESH2C_TIMESTAMP = "rspeventmesh2ctimestamp";

    public static final String REQ_SEND_EVENTMESH_IP = "reqsendeventmeship";
    public static final String REQ_RECEIVE_EVENTMESH_IP = "reqreceiveeventmeship";
    public static final String RSP_SEND_EVENTMESH_IP = "rspsendeventmeship";
    public static final String RSP_RECEIVE_EVENTMESH_IP = "rspreceiveeventmeship";

    public static final String RSP_SYS = "rsp0sys";
    public static final String RSP_IP = "rsp0ip";
    public static final String RSP_IDC = "rsp0idc";
    public static final String RSP_GROUP = "rsp0group";
    public static final String RSP_URL = "rsp0url";

    public static final String REQ_SYS = "req0sys";
    public static final String REQ_IP = "req0ip";
    public static final String REQ_IDC = "req0idc";
    public static final String REQ_GROUP = "req0group";

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
    public static final String TTL = "ttl";

    public static final String TAG = "TAG";

    public static final String MANAGE_SUBSYSTEM = "subsystem";
    public static final String MANAGE_IP = "ip";
    public static final String MANAGE_PORT = "port";
    public static final String MANAGE_DEST_IP = "desteventmeshIp";
    public static final String MANAGE_DEST_PORT = "desteventmeshport";
    public static final String MANAGE_PATH = "path";
    public static final String MANAGE_GROUP = "group";
    public static final String MANAGE_PURPOSE = "purpose";
    public static final String MANAGE_TOPIC = "topic";

    public static final String EVENTMESH_SEND_BACK_TIMES = "eventmeshdendbacktimes";

    public static final String EVENTMESH_SEND_BACK_IP = "eventmeshsendbackip";

    public static final String EVENTMESH_REGISTRY_ADDR_KEY = "eventMeshRegistryAddr";

    public static final int DEFAULT_TIME_OUT_MILLS = 5 * 1000;

    public static final String RR_REPLY_TOPIC = "rr-reply-topic";

    public static final String PROPERTY_MESSAGE_CLUSTER = "cluster";

    public static final String PROPERTY_MESSAGE_TTL = "ttl";

    public static final String PROPERTY_MESSAGE_KEYS = "keys";

    public static final String PROPERTY_MESSAGE_REPLY_TO = "REPLY_TO";  //requester clientId

    public static final String PROPERTY_RR_REQUEST_ID = "RR_REQUEST_UNIQ_ID";

    public static final String LEAVE_TIME = "leave" + Constants.MESSAGE_PROP_SEPARATOR + "time";            //leaveBrokerTime
    public static final String ARRIVE_TIME = "arrive" + Constants.MESSAGE_PROP_SEPARATOR + "time";
    public static final String STORE_TIME = "store" + Constants.MESSAGE_PROP_SEPARATOR + "time";


}
