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

package org.apache.eventmesh.client.common.constant;

/**
 * @Author: moxing
 * @Date: 2022/7/18 14:03
 * @Description:
 */
public class ZooKeeperConstant {

    public static final String SESSION_TIMEOUT_MS = "sessionTimeoutMs";
    public static final String CONNECTION_TIMEOUT_MS = "connectionTimeoutMs";
    public static final String SLEEP_MS_BETWEEN_RETRIES = "sleepMsBetweenRetries";

    public static final String NAME_SPACE = "eventmesh";

    public static final String SEPARATOR = "/";

    public static final int SPLIT_LENGTH = 3;

    public static final Long INIT_TIMEOUT = 10 * 1000L;

}
