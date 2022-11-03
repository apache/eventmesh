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

package org.apache.eventmesh.common;

public class ExampleConstants {

    public static final String CONFIG_FILE_NAME = "application.properties";
    public static final String CLOUDEVENT_CONTENT_TYPE = "application/cloudevents+json";

    public static final String EVENTMESH_IP = "eventmesh.ip";
    public static final String EVENTMESH_HTTP_PORT = "eventmesh.http.port";
    public static final String EVENTMESH_TCP_PORT = "eventmesh.tcp.port";
    public static final String EVENTMESH_GRPC_PORT = "eventmesh.grpc.port";

    public static final String DEFAULT_EVENTMESH_IP = "127.0.0.1";
    public static final String DEFAULT_EVENTMESH_IP_PORT = "127.0.0.1:10105";

    public static final String EVENTMESH_GRPC_ASYNC_TEST_TOPIC = "TEST-TOPIC-GRPC-ASYNC";
    public static final String EVENTMESH_GRPC_RR_TEST_TOPIC = "TEST-TOPIC-GRPC-RR";
    public static final String EVENTMESH_GRPC_BROADCAT_TEST_TOPIC = "TEST-TOPIC-GRPC-BROADCAST";
    public static final String EVENTMESH_HTTP_ASYNC_TEST_TOPIC = "TEST-TOPIC-HTTP-ASYNC";
    public static final String EVENTMESH_HTTP_SYNC_TEST_TOPIC = "TEST-TOPIC-HTTP-SYNC";
    public static final String EVENTMESH_TCP_ASYNC_TEST_TOPIC = "TEST-TOPIC-TCP-ASYNC";
    public static final String EVENTMESH_TCP_SYNC_TEST_TOPIC = "TEST-TOPIC-TCP-SYNC";
    public static final String EVENTMESH_TCP_BROADCAST_TEST_TOPIC = "TEST-TOPIC-TCP-BROADCAST";

    public static final String DEFAULT_EVENTMESH_TEST_PRODUCER_GROUP = "EventMeshTest-producerGroup";
    public static final String DEFAULT_EVENTMESH_TEST_CONSUMER_GROUP = "EventMeshTest-consumerGroup";

    public static final String SERVER_NAME = "server.name";

    public static final String EVENTMESH_CATALOG_NAME = "eventmesh.catalog.name";

    public static final String EVENTMESH_WORKFLOW_NAME = "eventmesh.workflow.name";

    public static final String EVENTMESH_SELECTOR_NACOS_ADDRESS = "eventmesh.selector.nacos.address";

    public static final String EVENTMESH_SELECTOR_TYPE = "eventmesh.selector.type";

	public static final String ENV = "P";
	public static final String IDC = "FT";
	public static final String SUB_SYS = "1234";
	public static final String SERVER_PORT = "server.port";
}
