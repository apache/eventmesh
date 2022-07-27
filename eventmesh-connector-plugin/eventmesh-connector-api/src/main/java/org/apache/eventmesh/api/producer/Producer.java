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

package org.apache.eventmesh.api.producer;

import org.apache.eventmesh.api.LifeCycle;
import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

import java.util.Properties;

import io.cloudevents.CloudEvent;

/**
 * Producer Interface.
 */
@EventMeshSPI(isSingleton = false, eventMeshExtensionType = EventMeshExtensionType.CONNECTOR)
public interface Producer extends LifeCycle {

    void init(Properties properties) throws Exception;

    void publish(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception;

    void sendOneway(final CloudEvent cloudEvent);

    void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception;

    boolean reply(final CloudEvent cloudEvent, final SendCallback sendCallback) throws Exception;

    void checkTopicExist(String topic) throws Exception;

    void setExtFields();
}
