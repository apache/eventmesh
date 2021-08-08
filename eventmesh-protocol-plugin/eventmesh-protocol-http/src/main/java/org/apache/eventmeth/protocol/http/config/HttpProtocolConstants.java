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

package org.apache.eventmeth.protocol.http.config;

public enum HttpProtocolConstants {
    ;

    public static final String HTTP_CONFIGURATION_FILE = "eventmesh-protocol-http.yml";

    public static final String DEFAULT_CHARSET = "UTF-8";

    public static final String PROTOCOL_HTTP = "http";

    public static final int DEFAULT_PUSH_RETRY_TIMES = 3;

    public static final String TAG = "TAG";

    //default TTL 4 hours
    public static final Integer DEFAULT_MSG_TTL_MILLS = 14400000;

    public static final long DEFAULT_TIMEOUT_IN_MILLISECONDS = 3000L;

    public static final int DEFAULT_FASTFAIL_TIMEOUT_IN_MILLISECONDS = 100;

    public static final long DEFAULT_PUSH_RETRY_TIME_DISTANCE_IN_MILLSECONDS = 3000L;

    public static final String REQ_C2EVENTMESH_TIMESTAMP = "req_c2eventMesh_timestamp";
    public static final String REQ_EVENTMESH2MQ_TIMESTAMP = "req_eventMesh2mq_timestamp";
    public static final String REQ_MQ2EVENTMESH_TIMESTAMP = "req_mq2eventMesh_timestamp";
    public static final String REQ_EVENTMESH2C_TIMESTAMP = "req_eventMesh2c_timestamp";
    public static final String RSP_MQ2EVENTMESH_TIMESTAMP = "rsp_mq2eventMesh_timestamp";
    public static final String RSP_EVENTMESH2C_TIMESTAMP = "rsp_eventMesh2c_timestamp";

    public static final String STORE_TIMESTAMP = "STORE_TIME";

    public static final String RR_REPLY_TOPIC = "rr-reply-topic";

    public static final String PROPERTY_MESSAGE_CLUSTER = "CLUSTER";
}
