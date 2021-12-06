package org.apache.eventmesh.connector.rocketmq.utils;


import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.connector.rocketmq.cloudevent.impl.RocketMQHeaders;

import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageAccessor;
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
        MessageAccessor.putProperty(message,buildCloudEventPropertyKey(Constants.PROPERTY_MESSAGE_BORN_HOST),
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

        return message;
    }



    private static String buildCloudEventPropertyKey(String propName) {
        return RocketMQHeaders.CE_PREFIX + propName;
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
                (int) Integer.valueOf(message.getProperty(buildCloudEventPropertyKey(Constants.PROPERTY_MESSAGE_QUEUE_ID)));
            long queueOffset = (long) Long.valueOf(
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
