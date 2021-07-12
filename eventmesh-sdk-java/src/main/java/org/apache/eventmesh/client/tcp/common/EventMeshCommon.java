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
     * 打印线程池状态的间隔时间
     */
    public static int PRINTTHREADPOOLSTATE_INTEVAL = 1;

    /**
     * 数据上报给logServer间隔时间
     */
    public static int LOGSERVER_INTEVAL = 1 * 60 * 1000;

    /**
     * CLIENT端心跳间隔时间
     */
    public static int HEARTBEAT = 30 * 1000;

    /**
     * RR 废弃清理的时间间隔
     */
    public static int REQUEST_CONTEXT_CLEAN_EXPIRE = 60 * 1000;

    /**
     * METRICS 废弃清理的时间间隔
     */
    public static int METRICS_CLEAN_EXPIRE = 2 * 1000;

    /**
     * SESSION 废弃清理的时间间隔
     */
    public static int SESSION_CLEAN_EXPIRE = 5 * 1000;

    /**
     * EventMesh校验用户名
     */
    public static String EventMesh_USER = "EventMesh";

    /**
     * EventMesh校验用户密码
     */
    public static String EventMesh_PASS = "EventMesh@123";

    /**
     * 服务器共有的超时时间
     */
    public static int DEFAULT_TIME_OUT_MILLS = 20 * 1000;

    /**
     * PUB用途的USERAGENT
     */
    public static String USER_AGENT_PURPOSE_PUB = "pub";

    /**
     * SUB用途的USERAGENT
     */
    public static String USER_AGENT_PURPOSE_SUB = "sub";

    /**
     * 集群消费者消费组前缀
     */
    public static String PREFIX_CONSUMER_GROUP_CLUSTERING = "clustering";

    /**
     * 广播消费者消费组前缀
     */
    public static String PREFIX_CONSUMER_GROUP_BROADCASTING = "broadcast";

    /**
     * RR 的请求消息存储的集群具体IP地址和端口
     */
    public static String KEY_RR_REQ_STROE_ADDRESS = "rr_req_store_addr";

    /**
     * RR请求消息存储在WEMQ上的MSGID
     */
    public static String KEY_RR_REQ_WEMQ_MSG_ID = "rr_req_wemq_msg_id";

    /**
     * 如果多个TOPIC都是旁路消息，下发给C时 都按这个 TOPIC 给，C匹配到这个 bq-bypass 则认为是旁路，则按旁路解析
     */
    public static String KEY_BYPASS_MSG_ORIGINAL_TOPIC = "original_topic";

    /**
     * Client端查询服务器的server级别的统计数据的标识KEY
     */
    public static String KEY_QUERY_SERVER_STATISTICS = "server-statistic";

    /**
     * Client端查询服务器的session级别的统计数据的标识KEY
     */
    public static String KEY_QUERY_SESSION_STATISTICS = "session-statistic";

    /**
     * SESSION 统计TPS 前缀区分
     */
    public static String PREFIX_SESSION_TPS_STAT_RRSEND = "rr_send_tps_";

    public static String PREFIX_SESSION_TPS_STAT_RRREV = "rr_rev_tps_";

    public static String PREFIX_SESSION_TPS_STAT_EVENTSEND = "event_send_tps_";

    public static String PREFIX_SESSION_TPS_STAT_EVENTREV = "event_rev_tps_";

}
