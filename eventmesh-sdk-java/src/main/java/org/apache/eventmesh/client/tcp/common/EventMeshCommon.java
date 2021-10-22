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

package org.apache.eventmesh.client.tcp.common;

public class EventMeshCommon {

    /**
     * Interval for printing thread pool status
     */
    public static int PRINTTHREADPOOLSTATE_INTEVAL = 1;

    /**
     * Interval between data reporting to logServer
     */
    public static int LOGSERVER_INTEVAL = 1 * 60 * 1000;

    /**
     * CLIENT heartbeat interval
     */
    public static int HEARTBEAT = 30 * 1000;

    /**
     * Obsolete RR cleanup interval
     */
    public static int REQUEST_CONTEXT_CLEAN_EXPIRE = 60 * 1000;

    /**
     * Obsolete METRICS cleanup interval
     */
    public static int METRICS_CLEAN_EXPIRE = 2 * 1000;

    /**
     * Obsolete SESSION cleanup interval
     */
    public static int SESSION_CLEAN_EXPIRE = 5 * 1000;

    /**
     * Username used for EventMesh verification
     */
    public static String EventMesh_USER = "";

    /**
     * Password used for EventMesh verification
     */
    public static String EventMesh_PASS = "";

    /**
     * Timeout time shared by the server
     */
    public static int DEFAULT_TIME_OUT_MILLS = 20 * 1000;

    /**
     * USERAGENT for PUB
     */
    public static String USER_AGENT_PURPOSE_PUB = "pub";

    /**
     * USERAGENT for SUB
     */
    public static String USER_AGENT_PURPOSE_SUB = "sub";

    /**
     * Consumer group prefix of clustering consumers
     */
    public static String PREFIX_CONSUMER_GROUP_CLUSTERING = "clustering";

    /**
     * Consumer group prefix of broadcasting consumers
     */
    public static String PREFIX_CONSUMER_GROUP_BROADCASTING = "broadcast";

    /**
     * The specific IP address and port of the cluster where the RR messages are stored
     */
    public static String KEY_RR_REQ_STROE_ADDRESS = "rr_req_store_addr";

    /**
     * MSGID of RR requests stored on WEMQ
     */
    public static String KEY_RR_REQ_WEMQ_MSG_ID = "rr_req_wemq_msg_id";

    /**
     * If messages in multiple TOPICs are bypass messages, they will all be sent to C according to this TOPIC. If C
     * matches this bq-bypass, it will be considered as bypass, and it will be parsed by bypass.
     */
    public static String KEY_BYPASS_MSG_ORIGINAL_TOPIC = "original_topic";

    /**
     * Identification KEY: Client side queries server-level statistics of the server
     */
    public static String KEY_QUERY_SERVER_STATISTICS = "server-statistic";

    /**
     * Identification KEY: Client side queries session-level statistics of the server
     */
    public static String KEY_QUERY_SESSION_STATISTICS = "session-statistic";

    /**
     * Used to count SESSION TPS. Use prefixes to distinguish TPS of different types of messages.
     */
    public static String PREFIX_SESSION_TPS_STAT_RRSEND = "rr_send_tps_";

    public static String PREFIX_SESSION_TPS_STAT_RRREV = "rr_rev_tps_";

    public static String PREFIX_SESSION_TPS_STAT_EVENTSEND = "event_send_tps_";

    public static String PREFIX_SESSION_TPS_STAT_EVENTREV = "event_rev_tps_";
}
