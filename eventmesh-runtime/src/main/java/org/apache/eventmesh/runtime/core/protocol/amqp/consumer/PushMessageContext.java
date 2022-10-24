package org.apache.eventmesh.runtime.core.protocol.amqp.consumer;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import org.apache.eventmesh.runtime.util.ServerGlobal;

import io.cloudevents.CloudEvent;

import lombok.Data;


/**
 * context of message when pushing
 */
@Data
public class PushMessageContext {

    /**
     * cloudEvent receive from mesh mq
     */
    private CloudEvent cloudEvent;

    /**
     * a global messageId
     */
    private String messageId;

    /**
     * times that retry to be pushed
     */
    private int retryTimes;

    private MQConsumerWrapper mqConsumerWrapper;

    private AbstractContext consumeConcurrentlyContext;

    public PushMessageContext(CloudEvent cloudEvent, MQConsumerWrapper mqConsumerWrapper, AbstractContext consumeConcurrentlyContext) {
        this.cloudEvent = cloudEvent;
        this.messageId = ServerGlobal.getInstance().getMsgCounter().toString();
        this.retryTimes = 0;
        this.mqConsumerWrapper = mqConsumerWrapper;
        this.consumeConcurrentlyContext = consumeConcurrentlyContext;
    }
}