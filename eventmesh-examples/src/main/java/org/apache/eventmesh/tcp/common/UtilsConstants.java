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

package org.apache.eventmesh.tcp.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UtilsConstants {

    public static final String ENV = "test";
    public static final String HOST = "localhost";
    public static final Integer PASSWORD_LENGTH = 8;
    public static final String USER_NAME = "PU4283";
    public static final String GROUP = "EventmeshTestGroup";
    public static final String PATH = "/data/app/umg_proxy";
    public static final Integer PORT_1 = 8362;
    public static final Integer PORT_2 = 9362;
    public static final String SUB_SYSTEM_1 = "5023";
    public static final String SUB_SYSTEM_2 = "5017";
    public static final Integer PID_1 = 32_893;
    public static final Integer PID_2 = 42_893;
    public static final String VERSION = "2.0.11";
    public static final String IDC = "FT";
    /**
     * PROPERTY KEY NAME .
     */
    public static final String MSG_TYPE = "msgtype";
    public static final String TTL = "ttl";
    public static final String KEYS = "keys";
    public static final String REPLY_TO = "replyto";
    public static final String PROPERTY_MESSAGE_REPLY_TO = "propertymessagereplyto";
    public static final String CONTENT = "content";

}
