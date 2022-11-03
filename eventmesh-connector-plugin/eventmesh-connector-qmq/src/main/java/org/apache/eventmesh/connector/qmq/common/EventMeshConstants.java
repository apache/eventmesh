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

package org.apache.eventmesh.connector.qmq.common;

public class EventMeshConstants {
    public static final String EVENTMESH_CONF_FILE = "qmq-client.properties";

    public static final String QMQ_METASERVER_KEY = "eventMesh.server.qmq.metaserver";

    public static final String QMQ_APPCODE_KEY = "eventMesh.server.qmq.appcode";

    public static final String QMQ_CONSUMERGROUP_KEY = "eventMesh.server.qmq.consumergroup";

    public static final String QMQ_CONSUMER_THREADPOOLSIZE_KEY = "eventMesh.server.qmq.consumer.threadpoolsize";

    public static final String QMQ_CONSUMER_THREADPOOLQUEUESIZE_KEY = "eventMesh.server.qmq.consumer.threadpoolqueuesize";

    public static final String QMQ_MSG_BODY = "qmqMsgBody";

    public static final String QMQ_IDC_KEY = "eventMesh.server.qmq.idc";

    public static final String QMQ_PRODUCER_THREADCOUNT_KEY = "eventMesh.server.qmq.producer.threadcount";

    public static final String QMQ_PRODUCER_BATCHSIZE_KEY = "30";


    public static final String QMQ_PRODUCER_TRYCOUNT_KEY = "eventMesh.server.qmq.producer.trycount";

    public static final String QMQ_PRODUCER_MAXQUEUESIZE_KEY = "eventMesh.server.qmq.producer.maxqueuesize";


}
