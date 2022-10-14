/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.eventmesh.runtime.core.protocol.amqp.push;

import org.apache.eventmesh.runtime.core.protocol.amqp.processor.AmqpConnection;

import java.io.IOException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.client.impl.Method;

import lombok.extern.log4j.Log4j2;

/**
 * Used to process command output.
 */
@Log4j2
public class AmqpMessageSender {

    private final AmqpConnection connection;

    //
    public AmqpMessageSender(AmqpConnection connection) {
        this.connection = connection;
    }

    public long writeDeliver(final AmqpMessageData message, int channelId,
        boolean isRedelivered, long deliveryTag,
        String consumerTag) throws IOException {
        MessagePublishInfo publishInfo = message.getPublishInfo();
        AMQP.Basic.Deliver deliver = connection.getCommandFactory().createBasicDeliverBody(consumerTag, deliveryTag, isRedelivered,
            publishInfo.getExchange(), publishInfo.getRoutingKey());
        return writeMessage(message, (Method) deliver, channelId);

    }

    public long writeGetOk(final AmqpMessageData message, int channelId,
        boolean isRedelivered, long deliveryTag, int messageCount) throws IOException {
        MessagePublishInfo publishInfo = message.getPublishInfo();
        AMQP.Basic.GetOk getOk = connection.getCommandFactory().createBasicGetOkBody(deliveryTag, isRedelivered,
            publishInfo.getExchange(), publishInfo.getRoutingKey(), messageCount);
        return writeMessage(message, (Method) getOk, channelId);
    }

    private long writeMessage(final AmqpMessageData message, final Method method, int channelId) throws IOException {
        AmqpMessageFrame messageFrame = new AmqpMessageFrame();
        byte[] body = message.getContentBody();

        Frame headerFrame = message.getContentHeader().toFrame(channelId, body.length);

        int frameMax = connection.getMaxFrameSize();
        boolean cappedFrameMax = frameMax > 0;
        int bodyPayloadMax = cappedFrameMax ? frameMax - AMQPFrameWrapper.NON_BODY_SIZE : body.length;

        if (cappedFrameMax && headerFrame.size() > frameMax) {
            String msg = String.format("Content headers exceeded max frame size: %d > %d", headerFrame.size(), frameMax);
            throw new IllegalArgumentException(msg);
        }
        messageFrame.setMethod(AMQPFrameWrapper.get(method.toFrame(channelId)));
        messageFrame.setHeader(AMQPFrameWrapper.get(headerFrame));

        for (int offset = 0; offset < body.length; offset += bodyPayloadMax) {
            int remaining = body.length - offset;

            int fragmentLength = (remaining < bodyPayloadMax) ? remaining
                : bodyPayloadMax;
            Frame frame = Frame.fromBodyFragment(channelId, body,
                offset, fragmentLength);
            messageFrame.addContent(AMQPFrameWrapper.get(frame));
        }
        connection.writeFrame(messageFrame);
        return body.length;
    }

    public void writeReturn(final AmqpMessageData message, int channelId, int replyCode,
        String replyText) throws IOException {

        AMQP.Basic.Return returnBody = connection.getCommandFactory().createBasicReturnBody(replyCode, replyText
            , message.getPublishInfo().getExchange(), message.getPublishInfo().getRoutingKey());

        writeMessage(message, (Method) returnBody, channelId);
    }

}
