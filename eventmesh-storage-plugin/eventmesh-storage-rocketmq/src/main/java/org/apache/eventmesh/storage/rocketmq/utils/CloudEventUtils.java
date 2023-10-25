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

package org.apache.eventmesh.storage.rocketmq.utils;

import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.common.Constants;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageAccessor;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;
import lombok.experimental.UtilityClass;

@Slf4j
@UtilityClass
public class CloudEventUtils {

    public SendResult convertSendResult(
        org.apache.rocketmq.client.producer.SendResult rmqResult) {
        SendResult sendResult = new SendResult();
        sendResult.setTopic(rmqResult.getMessageQueue().getTopic());
        sendResult.setMessageId(rmqResult.getMsgId());
        return sendResult;
    }

    public Message msgConvert(MessageExt rmqMsg) {
        Message message = new Message();
        initProperty(rmqMsg, message, MessageExt::getTopic, Message::setTopic);
        initProperty(rmqMsg, message, MessageExt::getKeys, Message::setKeys);
        initProperty(rmqMsg, message, MessageExt::getTags, Message::setTags);
        if (rmqMsg.getBody() != null) {
            message.setBody(rmqMsg.getBody());
        }
        rmqMsg.getProperties().forEach((k, v) -> MessageAccessor.putProperty(message, k, v));

        if (rmqMsg.getMsgId() != null) {
            MessageAccessor.putProperty(message, Constants.PROPERTY_MESSAGE_MESSAGE_ID, rmqMsg.getMsgId());
        }

        if (rmqMsg.getTopic() != null) {
            MessageAccessor.putProperty(message, Constants.PROPERTY_MESSAGE_DESTINATION, rmqMsg.getTopic());
        }

        MessageAccessor.putProperty(message, Constants.PROPERTY_MESSAGE_BORN_HOST, String.valueOf(rmqMsg.getBornHost()));
        MessageAccessor.putProperty(message, Constants.PROPERTY_MESSAGE_BORN_TIMESTAMP,
            String.valueOf(rmqMsg.getBornTimestamp()));
        MessageAccessor.putProperty(message, Constants.PROPERTY_MESSAGE_STORE_HOST,
            String.valueOf(rmqMsg.getStoreHost()));
        MessageAccessor.putProperty(message, Constants.PROPERTY_MESSAGE_STORE_TIMESTAMP,
            String.valueOf(rmqMsg.getStoreTimestamp()));

        // use in manual ack
        MessageAccessor.putProperty(message, Constants.PROPERTY_MESSAGE_QUEUE_ID, String.valueOf(rmqMsg.getQueueId()));
        MessageAccessor.putProperty(message, Constants.PROPERTY_MESSAGE_QUEUE_OFFSET,
            String.valueOf(rmqMsg.getQueueOffset()));

        for (String sysPropKey : MessageConst.STRING_HASH_SET) {
            if (StringUtils.isNotEmpty(message.getProperty(sysPropKey))) {
                String prop = message.getProperty(sysPropKey);
                String tmpPropKey = sysPropKey.toLowerCase().replace("_", Constants.MESSAGE_PROP_SEPARATOR);
                MessageAccessor.putProperty(message, tmpPropKey, prop);
                message.getProperties().remove(sysPropKey);
            }
        }

        return message;
    }

    public MessageExt msgConvertExt(Message message) {
        MessageExt rmqMessageExt = new MessageExt();
        try {
            initProperty(message, rmqMessageExt, Message::getKeys, Message::setKeys);
            initProperty(message, rmqMessageExt, Message::getTags, Message::setTags);

            if (message.getBody() != null) {
                rmqMessageExt.setBody(message.getBody());
            }

            // All destinations in RocketMQ use Topic
            rmqMessageExt.setTopic(message.getTopic());

            int queueId = Integer.parseInt(message.getProperty(Constants.PROPERTY_MESSAGE_QUEUE_ID));
            long queueOffset = Long.parseLong(message.getProperty(Constants.PROPERTY_MESSAGE_QUEUE_OFFSET));
            // use in manual ack
            rmqMessageExt.setQueueId(queueId);
            rmqMessageExt.setQueueOffset(queueOffset);

            message.getProperties().forEach((k, v) -> MessageAccessor.putProperty(rmqMessageExt, k, v));
        } catch (Exception e) {
            log.error("Error with msgConvertExt", e);
        }
        return rmqMessageExt;
    }

    /**
     * Populate the target with properties whose source is not empty
     *
     * @param source     source
     * @param target     target
     * @param function   function
     * @param biConsumer biConsumer
     * @param <T>        t
     * @param <V>        v
     */
    private <T, V> void initProperty(T source, V target, Function<T, String> function, BiConsumer<V, String> biConsumer) {
        String apply = function.apply(source);
        if (Objects.nonNull(apply)) {
            biConsumer.accept(target, apply);
        }
    }

}
