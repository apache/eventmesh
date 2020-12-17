package com.webank.eventmesh.api.consumer;

import com.webank.eventmesh.api.AbstractContext;
import com.webank.eventmesh.common.config.CommonConfiguration;
import io.openmessaging.consumer.MessageListener;
import io.openmessaging.Message;
import io.openmessaging.consumer.PushConsumer;

import java.util.List;

public interface MeshMQPushConsumer extends PushConsumer {

    void start() throws Exception;

//    void updateOffset(List<MessageExt> msgs, ConsumeConcurrentlyContext context);

    void updateOffset(List<Message> msgs, AbstractContext context);

    void init(boolean isBroadcast, CommonConfiguration commonConfiguration,
              String consumerGroup) throws Exception;

//    void registerMessageListener(MessageListenerConcurrently messageListenerConcurrently);

    void subscribe(String topic, final MessageListener listener) throws Exception;

    void unsubscribe(String topic) throws Exception;

    boolean isPause();

    void pause();

    void setInstanceName(String instanceName);

    AbstractContext getContext();
}
