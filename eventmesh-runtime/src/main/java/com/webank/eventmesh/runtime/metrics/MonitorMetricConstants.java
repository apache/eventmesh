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

package com.webank.eventmesh.runtime.metrics;

public class MonitorMetricConstants {
    public static final String EVENTMESH_MONITOR_FORMAT_COMMON = "{\"protocol\":\"%s\",\"s\":\"%s\",\"t\":\"%s\"}";

    public static final String EVENTMESH_TCP_MONITOR_FORMAT_THREADPOOL = "{\"threadPoolName\":\"%s\",\"s\":\"%s\",\"t\":\"%s\"}";

    public static final String CLIENT_2_EVENTMESH_TPS = "client2eventMeshTPS";
    public static final String EVENTMESH_2_MQ_TPS = "eventMesh2mqTPS";
    public static final String MQ_2_EVENTMESH_TPS = "mq2eventMeshTPS";
    public static final String EVENTMESH_2_CLIENT_TPS = "eventMesh2clientTPS";
    public static final String ALL_TPS = "allTPS";
    public static final String CONNECTION = "connection";
    public static final String SUB_TOPIC_NUM = "subTopicNum";

    public static final String RETRY_QUEUE_SIZE = "retryQueueSize";


    public static final String QUEUE_SIZE = "queueSize";
    public static final String POOL_SIZE = "poolSize";
    public static final String ACTIVE_COUNT = "activeCount";
    public static final String COMPLETED_TASK = "completedTask";
}
