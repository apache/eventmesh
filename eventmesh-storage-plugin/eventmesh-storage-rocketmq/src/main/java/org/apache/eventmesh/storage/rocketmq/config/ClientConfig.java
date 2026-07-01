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

package org.apache.eventmesh.storage.rocketmq.config;

import org.apache.eventmesh.storage.rocketmq.domain.NonStandardKeys;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class ClientConfig implements NonStandardKeys {

    private String driverImpl;
    private String accessPoints;
    private String namespace;
    private String producerId;
    private String consumerId;
    private int operationTimeout = 5000;
    private String region;
    private String routingSource;
    private String routingDestination;
    private String routingExpression;
    private String rmqConsumerGroup;
    private String rmqProducerGroup = "__OMS_PRODUCER_DEFAULT_GROUP";
    private int rmqMaxRedeliveryTimes = 16;
    private int rmqMessageConsumeTimeout = 15; // In minutes
    private int rmqMaxConsumeThreadNums = 64;
    private int rmqMinConsumeThreadNums = 20;
    private String rmqMessageDestination;
    private int rmqPullMessageBatchNums = 32;
    private int rmqPullMessageCacheCapacity = 1000;
    private String messageModel;

}
