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
     * CLIENT heartbeat interval
     */
    public static final int HEARTBEAT = 30 * 1000;

    /**
     * Timeout time shared by the server
     */
    public static final int DEFAULT_TIME_OUT_MILLS = 20 * 1000;

    /**
     * USERAGENT for PUB
     */
    public static final String USER_AGENT_PURPOSE_PUB = "pub";

    /**
     * USERAGENT for SUB
     */
    public static final String USER_AGENT_PURPOSE_SUB = "sub";

    // protocol type
    public static final String CLOUD_EVENTS_PROTOCOL_NAME = "cloudevents";
    public static final String EM_MESSAGE_PROTOCOL_NAME = "eventmeshmessage";
    public static final String OPEN_MESSAGE_PROTOCOL_NAME = "openmessage";
}
