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

<<<<<<<< HEAD:eventmesh-admin/eventmesh-admin-rocketmq/src/main/java/org/apache/eventmesh/admin/rocketmq/Constants.java
package org.apache.eventmesh.admin.rocketmq;

public class Constants {

    public static final String TOPIC_MANAGE_PATH = "/topicManage";
========
package org.apache.eventmesh.connector.rocketmq.domain;

public interface RocketMQConstants {

    /**
     * Key of scheduled message delivery time
     */
    String START_DELIVER_TIME = "__STARTDELIVERTIME";

>>>>>>>> e4cff57da85093ca7a917f7edd86fa434000d5dc:eventmesh-connector-rocketmq/src/main/java/org/apache/eventmesh/connector/rocketmq/domain/RocketMQConstants.java
}
