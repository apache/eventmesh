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

package org.apache.eventmesh.connector.redis.producer;

import com.alibaba.fastjson.JSONArray;
import io.cloudevents.CloudEvent;
import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.api.producer.Producer;

import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.connector.redis.connector.RedisPubSubConnector;

@Slf4j
public class RedisProducerImpl extends RedisPubSubConnector implements Producer {

    @Override
    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        checkIsClosed();
        try {
            redisCommands.publish(cloudEvent.getSubject(), JSONArray.toJSONString(cloudEvent));
            SendResult sendResult = new SendResult();
            sendResult.setTopic(cloudEvent.getSubject());
            sendResult.setMessageId(cloudEvent.getId());
            sendCallback.onSuccess(sendResult);
        } catch (RuntimeException e) {
            sendCallback.onException(
                    OnExceptionContext.builder()
                            .topic(cloudEvent.getSubject())
                            .messageId(cloudEvent.getId())
                            .exception(new ConnectorRuntimeException(e))
                            .build());
        }
    }

    @Override
    public void sendOneway(CloudEvent cloudEvent) {
        checkIsClosed();
        redisCommands.publish(cloudEvent.getSubject(), JSONArray.toJSONString(cloudEvent));
    }

    @Override
    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception {
        throw new ConnectorRuntimeException("request is not supported");
    }

    @Override
    public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        throw new ConnectorRuntimeException("reply is not supported");
    }

    @Override
    public void checkTopicExist(String topic) throws Exception {
        throw new ConnectorRuntimeException("checkTopicExist is not supported");
    }

    @Override
    public void setExtFields() {
        throw new ConnectorRuntimeException("setExtFields is not supported");
    }
}
