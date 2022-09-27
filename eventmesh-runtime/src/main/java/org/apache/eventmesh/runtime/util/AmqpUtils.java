package org.apache.eventmesh.runtime.util;

import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.AMQData;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.AMQPFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AmqpUtils {
    private static final Logger logger = LoggerFactory.getLogger(AmqpUtils.class);

    /**
     * write frame back to client
     * @param frame
     */
    public static void writeFrame(AMQData frame) {

    }

    /**
     * send connection close frame to client
     * @param errorCode
     * @param message
     * @param channelId
     */
    public static void sendConnectionClose(int errorCode, String message, int channelId) {
        // TODO: 2022/9/21
    }

    /**
     * send connection close protocol to client
     * @param channelId
     * @param frame
     */
    public void sendConnectionClose(int channelId, AMQPFrame frame) {
        // TODO: 2022/9/21
        writeFrame(frame);
    }

}