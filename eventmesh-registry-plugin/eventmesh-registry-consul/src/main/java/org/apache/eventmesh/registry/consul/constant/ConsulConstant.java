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

package org.apache.eventmesh.registry.consul.constant;

public class ConsulConstant {

    public static final int DEFAULT_MAX_CONNECTIONS = 1000;

    public static final int DEFAULT_MAX_PER_ROUTE_CONNECTIONS = 500;

    /**
     * 3 sec
     */
    public static final int DEFAULT_CONNECTION_TIMEOUT = 3000;

    /**
     * 2 sec
     * 0 minutes for read timeout due to blocking queries timeout
     * <a href="https://www.consul.io/api/index.html#blocking-queries">...</a>
     */
    public static final int DEFAULT_READ_TIMEOUT = 2000;

    public static final String SERVICE_ADDR = "serverAddr";

    public static final String IP_PORT_SEPARATOR = ":";

    public static final String DEFAULT_GROUP = "DEFAULT_GROUP";


}
