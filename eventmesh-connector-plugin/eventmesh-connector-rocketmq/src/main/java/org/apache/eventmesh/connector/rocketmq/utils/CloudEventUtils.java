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

package org.apache.eventmesh.connector.rocketmq.utils;

import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.common.Constants;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageAccessor;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.Map;
import java.util.Set;

public class CloudEventUtils {

    public static SendResult convertSendResult(
        org.apache.rocketmq.client.producer.SendResult rmqResult) {
        SendResult sendResult = new SendResult();
        sendResult.setTopic(rmqResult.getMessageQueue().getTopic());
        sendResult.setMessageId(rmqResult.getMsgId());
        return sendResult;
    }


    public static Message msgConvert(MessageExt rmqMsg) {
        Message message = new Message();
        if (rmqMsg.getTopic() != null) {
            message.setTopic(rmqMsg.getTopic());
        }

        if (rmqMsg.getKeys() != null) {
            message.setKeys(rmqMsg.getKeys());
        }

        if (rmqMsg.getTags() != null) {
            message.setTags(rmqMsg.getTags());
        }

        if (rmqMsg.getBody() != null) {
            message.setBody(rmqMsg.getBody());
        }

        final Set<Map.Entry<String, String>> entries = rmqMsg.getProperties().entrySet();

        for (final Map.Entry<String, String> entry : entries) {
            MessageAccessor.putProperty(message, entry.getKey(), entry.getValue());
        }

        if (rmqMsg.getMsgId() != null) {
            MessageAccessor.putProperty(message, buildCloudEventPropertyKey(Constants.PROPERTY_MESSAGE_MESSAGE_ID),
                rmqMsg.getMsgId());
        }

        if (rmqMsg.getTopic() != null) {
            MessageAccessor.putProperty(message, buildCloudEventPropertyKey(Constants.PROPERTY_MESSAGE_DESTINATION),
                rmqMsg.getTopic());
        }

        //
        MessageAccessor.putProperty(message, buildCloudEventPropertyKey(Constants.PROPERTY_MESSAGE_BORN_HOST),
            String.valueOf(rmqMsg.getBornHost()));
        MessageAccessor.putProperty(message, buildCloudEventPropertyKey(Constants.PROPERTY_MESSAGE_BORN_TIMESTAMP),
            String.valueOf(rmqMsg.getBornTimestamp()));
        MessageAccessor.putProperty(message, buildCloudEventPropertyKey(Constants.PROPERTY_MESSAGE_STORE_HOST),
            String.valueOf(rmqMsg.getStoreHost()));
        MessageAccessor.putProperty(message, buildCloudEventPropertyKey(Constants.PROPERTY_MESSAGE_STORE_TIMESTAMP),
            String.valueOf(rmqMsg.getStoreTimestamp()));

        //use in manual ack
        MessageAccessor.putProperty(message, buildCloudEventPropertyKey(Constants.PROPERTY_MESSAGE_QUEUE_ID),
            String.valueOf(rmqMsg.getQueueId()));
        MessageAccessor.putProperty(message, buildCloudEventPropertyKey(Constants.PROPERTY_MESSAGE_QUEUE_OFFSET),
            String.valueOf(rmqMsg.getQueueOffset()));

        for (String sysPropKey : MessageConst.STRING_HASH_SET) {
            if (StringUtils.isNotEmpty(message.getProperty(sysPropKey))) {
                String prop = message.getProperty(sysPropKey);
                String tmpPropKey = sysPropKey.toLowerCase().replaceAll("_", Constants.MESSAGE_PROP_SEPARATOR);
                MessageAccessor.putProperty(message, tmpPropKey, prop);
                message.getProperties().remove(sysPropKey);
            }
        }

        return message;
    }



    private static String buildCloudEventPropertyKey(String propName) {
        //return RocketMQHeaders.CE_PREFIX + propName;
        return propName;
    }

    public static org.apache.rocketmq.common.message.MessageExt msgConvertExt(Message message) {

        org.apache.rocketmq.common.message.MessageExt rmqMessageExt =
            new org.apache.rocketmq.common.message.MessageExt();
        try {
            if (message.getKeys() != null) {
                rmqMessageExt.setKeys(message.getKeys());
            }
            if (message.getTags() != null) {
                rmqMessageExt.setTags(message.getTags());
            }


            if (message.getBody() != null) {
                rmqMessageExt.setBody(message.getBody());
            }


            //All destinations in RocketMQ use Topic
            rmqMessageExt.setTopic(message.getTopic());

            int queueId =
                Integer.parseInt(message.getProperty(buildCloudEventPropertyKey(Constants.PROPERTY_MESSAGE_QUEUE_ID)));
            long queueOffset = Long.parseLong(
                message.getProperty(buildCloudEventPropertyKey(Constants.PROPERTY_MESSAGE_QUEUE_OFFSET)));
            //use in manual ack
            rmqMessageExt.setQueueId(queueId);
            rmqMessageExt.setQueueOffset(queueOffset);
            Map<String, String> properties = message.getProperties();
            for (final Map.Entry<String, String> entry : properties.entrySet()) {
                MessageAccessor.putProperty(rmqMessageExt, entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rmqMessageExt;

    }


}
