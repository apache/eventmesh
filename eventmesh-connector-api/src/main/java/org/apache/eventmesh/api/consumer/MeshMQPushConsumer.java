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

package org.apache.eventmesh.api.consumer;

import java.util.List;
import java.util.Properties;

import io.openmessaging.api.AsyncMessageListener;
import io.openmessaging.api.Consumer;
import io.openmessaging.api.Message;

import org.apache.eventmesh.api.AbstractContext;

public interface MeshMQPushConsumer extends Consumer {

    void init(Properties keyValue) throws Exception;

    void updateOffset(List<Message> msgs, AbstractContext context);

//    void registerMessageListener(MessageListenerConcurrently messageListenerConcurrently);

    void subscribe(String topic, final AsyncMessageListener listener) throws Exception;

    @Override
    void unsubscribe(String topic);
}
