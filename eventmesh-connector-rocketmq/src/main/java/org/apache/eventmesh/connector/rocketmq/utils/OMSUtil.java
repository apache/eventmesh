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

import com.webank.eventmesh.common.Constants;
import io.openmessaging.api.Message;
import io.openmessaging.api.OMSBuiltinKeys;
import io.openmessaging.api.SendResult;
import io.openmessaging.api.exception.OMSRuntimeException;
import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.message.MessageAccessor;
import org.apache.rocketmq.common.message.MessageExt;

import java.lang.reflect.Field;
import java.util.*;

public class OMSUtil {

    /**
     * Builds a OMS client instance name.
     *
     * @return a unique instance name
     */
    public static String buildInstanceName() {
        return Integer.toString(UtilAll.getPid()) + "%OpenMessaging" + "%" + System.nanoTime();
    }

    public static org.apache.rocketmq.common.message.Message msgConvert(Message omsMessage) {
        org.apache.rocketmq.common.message.Message rmqMessage = new org.apache.rocketmq.common.message.Message();
        if (omsMessage == null) {
            throw new OMSRuntimeException("'message' is null");
        } else {
            if (omsMessage.getTopic() != null) {
                rmqMessage.setTopic(omsMessage.getTopic());
            }
            if (omsMessage.getKey() != null) {
                rmqMessage.setKeys(omsMessage.getKey());
            }
            if (omsMessage.getTag() != null) {
                rmqMessage.setTags(omsMessage.getTag());
            }
            if (omsMessage.getStartDeliverTime() > 0L) {
                rmqMessage.putUserProperty("TIMER_DELIVER_MS", String.valueOf(omsMessage.getStartDeliverTime()));
            }

            if (omsMessage.getBody() != null) {
                rmqMessage.setBody(omsMessage.getBody());
            }

            if (omsMessage.getShardingKey() != null && !omsMessage.getShardingKey().isEmpty()) {
                rmqMessage.putUserProperty("__SHARDINGKEY", omsMessage.getShardingKey());
            }
        }
        Properties systemProperties = omsMessage.getSystemProperties();
        Properties userProperties = omsMessage.getUserProperties();

        //All destinations in RocketMQ use Topic
//        rmqMessage.setTopic(systemProperties.getProperty(BuiltinKeys.DESTINATION));

//        if (sysHeaders.containsKey(BuiltinKeys.START_TIME)) {
//            long deliverTime = sysHeaders.getLong(BuiltinKeys.START_TIME, 0);
//            if (deliverTime > 0) {
//                rmqMessage.putUserProperty(RocketMQConstants.START_DELIVER_TIME, String.valueOf(deliverTime));
//            }
//        }

        for (String key : userProperties.stringPropertyNames()) {
            MessageAccessor.putProperty(rmqMessage, key, userProperties.getProperty(key));
        }

        //System headers has a high priority
        for (String key : systemProperties.stringPropertyNames()) {
            MessageAccessor.putProperty(rmqMessage, key, systemProperties.getProperty(key));
        }

        return rmqMessage;
    }

    public static Message msgConvert(MessageExt rmqMsg) {
        Message message = new Message();
        if (rmqMsg.getTopic() != null) {
            message.setTopic(rmqMsg.getTopic());
        }

        if (rmqMsg.getKeys() != null) {
            message.setKey(rmqMsg.getKeys());
        }

        if (rmqMsg.getTags() != null) {
            message.setTag(rmqMsg.getTags());
        }

        if (rmqMsg.getBody() != null) {
            message.setBody(rmqMsg.getBody());
        }

        if (rmqMsg.getUserProperty("TIMER_DELIVER_MS") != null) {
            long ms = Long.parseLong(rmqMsg.getUserProperty("TIMER_DELIVER_MS"));
            rmqMsg.getProperties().remove("TIMER_DELIVER_MS");
            message.setStartDeliverTime(ms);
        }

        Properties systemProperties = new Properties();
        Properties userProperties = new Properties();


        final Set<Map.Entry<String, String>> entries = rmqMsg.getProperties().entrySet();

        for (final Map.Entry<String, String> entry : entries) {
            if (isOMSHeader(entry.getKey())) {
                //sysHeader
                systemProperties.put(entry.getKey(), entry.getValue());
            } else {
                //userHeader
                userProperties.put(entry.getKey(), entry.getValue());
            }
        }

        systemProperties.put(Constants.PROPERTY_MESSAGE_MESSAGE_ID, rmqMsg.getMsgId());

        systemProperties.put(Constants.PROPERTY_MESSAGE_DESTINATION, rmqMsg.getTopic());

//        omsMsg.putSysHeaders(BuiltinKeys.SEARCH_KEYS, rmqMsg.getKeys());
        systemProperties.put(Constants.PROPERTY_MESSAGE_BORN_HOST, String.valueOf(rmqMsg.getBornHost()));
        systemProperties.put(Constants.PROPERTY_MESSAGE_BORN_TIMESTAMP, rmqMsg.getBornTimestamp());
        systemProperties.put(Constants.PROPERTY_MESSAGE_STORE_HOST, String.valueOf(rmqMsg.getStoreHost()));
        systemProperties.put("STORE_TIMESTAMP", rmqMsg.getStoreTimestamp());

        //use in manual ack
        userProperties.put(Constants.PROPERTY_MESSAGE_QUEUE_ID, rmqMsg.getQueueId());
        userProperties.put(Constants.PROPERTY_MESSAGE_QUEUE_OFFSET, rmqMsg.getQueueOffset());

        message.setSystemProperties(systemProperties);
        message.setUserProperties(userProperties);

        return message;
    }

    public static org.apache.rocketmq.common.message.MessageExt msgConvertExt(Message omsMessage) {

        org.apache.rocketmq.common.message.MessageExt rmqMessageExt = new org.apache.rocketmq.common.message.MessageExt();
        try {
            if (omsMessage.getKey() != null) {
                rmqMessageExt.setKeys(omsMessage.getKey());
            }
            if (omsMessage.getTag() != null) {
                rmqMessageExt.setTags(omsMessage.getTag());
            }
            if (omsMessage.getStartDeliverTime() > 0L) {
                rmqMessageExt.putUserProperty("TIMER_DELIVER_MS", String.valueOf(omsMessage.getStartDeliverTime()));
            }

            if (omsMessage.getBody() != null) {
                rmqMessageExt.setBody(omsMessage.getBody());
            }

            if (omsMessage.getShardingKey() != null && !omsMessage.getShardingKey().isEmpty()) {
                rmqMessageExt.putUserProperty("__SHARDINGKEY", omsMessage.getShardingKey());
            }

            Properties systemProperties = omsMessage.getSystemProperties();
            Properties userProperties = omsMessage.getUserProperties();

            //All destinations in RocketMQ use Topic
            rmqMessageExt.setTopic(omsMessage.getTopic());

            int queueId = (int)userProperties.get(Constants.PROPERTY_MESSAGE_QUEUE_ID);
            long queueOffset = (long)userProperties.get(Constants.PROPERTY_MESSAGE_QUEUE_OFFSET);
            //use in manual ack
            rmqMessageExt.setQueueId(queueId);
            rmqMessageExt.setQueueOffset(queueOffset);

            for (String key : userProperties.stringPropertyNames()) {
                MessageAccessor.putProperty(rmqMessageExt, key, userProperties.getProperty(key));
            }

            //System headers has a high priority
            for (String key : systemProperties.stringPropertyNames()) {
                MessageAccessor.putProperty(rmqMessageExt, key, systemProperties.getProperty(key));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return rmqMessageExt;

    }

    public static boolean isOMSHeader(String value) {
        for (Field field : OMSBuiltinKeys.class.getDeclaredFields()) {
            try {
                if (field.get(OMSBuiltinKeys.class).equals(value)) {
                    return true;
                }
            } catch (IllegalAccessException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Convert a RocketMQ SEND_OK SendResult instance to a OMS SendResult.
     */
    public static SendResult sendResultConvert(org.apache.rocketmq.client.producer.SendResult rmqResult) {
        SendResult sendResult = new SendResult();
        sendResult.setTopic(rmqResult.getMessageQueue().getTopic());
        sendResult.setMessageId(rmqResult.getMsgId());
        return sendResult;
    }

//    public static KeyValue buildKeyValue(KeyValue... keyValues) {
//        KeyValue keyValue = OMS.newKeyValue();
//        for (KeyValue properties : keyValues) {
//            for (String key : properties.keySet()) {
//                keyValue.put(key, properties.getString(key));
//            }
//        }
//        return keyValue;
//    }

    /**
     * Returns an iterator that cycles indefinitely over the elements of {@code Iterable}.
     */
    public static <T> Iterator<T> cycle(final Iterable<T> iterable) {
        return new Iterator<T>() {
            Iterator<T> iterator = new Iterator<T>() {
                @Override
                public synchronized boolean hasNext() {
                    return false;
                }

                @Override
                public synchronized T next() {
                    throw new NoSuchElementException();
                }

                @Override
                public synchronized void remove() {
                    //Ignore
                }
            };

            @Override
            public synchronized boolean hasNext() {
                return iterator.hasNext() || iterable.iterator().hasNext();
            }

            @Override
            public synchronized T next() {
                if (!iterator.hasNext()) {
                    iterator = iterable.iterator();
                    if (!iterator.hasNext()) {
                        throw new NoSuchElementException();
                    }
                }
                return iterator.next();
            }

            @Override
            public synchronized void remove() {
                iterator.remove();
            }
        };
    }
}
