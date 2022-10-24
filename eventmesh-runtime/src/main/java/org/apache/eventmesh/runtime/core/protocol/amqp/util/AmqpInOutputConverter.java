package org.apache.eventmesh.runtime.core.protocol.amqp.util;

import org.apache.eventmesh.common.protocol.amqp.AmqpMessage;
import org.apache.eventmesh.runtime.core.protocol.amqp.processor.AmqpConnection;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.AMQData;

/**
 * used to convert input & output in Amqp
 */
public class AmqpInOutputConverter {

    private AmqpConnection amqpConnection;

    public AmqpInOutputConverter(AmqpConnection amqpConnection) {
        this.amqpConnection = amqpConnection;
    }

    public AMQData convertOutput(AmqpMessage amqpMessage) {
        AMQData amqData = null;
        // TODO: 2022/10/21 conversion
        return amqData;
    }

    public AmqpMessage convertInput(AMQData amqData) {
        AmqpMessage amqpMessage = new AmqpMessage();
        // TODO: 2022/10/21 conversion
        return amqpMessage;
    }
}