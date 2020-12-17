package com.webank.api.consumer;

import com.webank.api.AbstractContext;
import com.webank.eventmesh.common.config.CommonConfiguration;
import io.openmessaging.Message;
import io.openmessaging.consumer.MessageListener;
import io.openmessaging.consumer.PushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.List;

public interface MeshMQPushConsumer extends PushConsumer {

    void start() throws Exception;

    void updateOffset(List<Message> msgs, ConsumeConcurrentlyContext context);

    void init(boolean isBroadcast, CommonConfiguration commonConfiguration,
              String consumerGroup) throws Exception;

    void registerMessageListener(MessageListener messageListener);

    void subscribe(String topic) throws Exception;

    void unsubscribe(String topic) throws Exception;

    boolean isPause();

    void pause();

    void setInstanceName(String instanceName);

    AbstractContext getContext();
}
