package com.webank.eventmesh.api.consumer;

import com.webank.eventmesh.api.AbstractContext;
import io.openmessaging.api.AsyncMessageListener;
import io.openmessaging.api.MessageListener;
import io.openmessaging.api.Message;
import io.openmessaging.api.Consumer;

import java.util.List;
import java.util.Properties;

public interface MeshMQPushConsumer extends Consumer {

    void init(Properties keyValue) throws Exception;

    @Override
    void start();

//    void updateOffset(List<MessageExt> msgs, ConsumeConcurrentlyContext context);

    void updateOffset(List<Message> msgs, AbstractContext context);

//    void registerMessageListener(MessageListenerConcurrently messageListenerConcurrently);

    void subscribe(String topic, final AsyncMessageListener listener) throws Exception;

    @Override
    void unsubscribe(String topic);

    AbstractContext getContext();
}
