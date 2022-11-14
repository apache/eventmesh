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

package org.apache.eventmesh.runtime.core.protocol.amqp.consumer;


import org.apache.eventmesh.common.protocol.amqp.AmqpMessage;
import org.apache.eventmesh.runtime.core.protocol.amqp.processor.AmqpConnection;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.AMQData;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.AMQPFrame;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

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

    public long writeDeliver(final AmqpMessage message, int channelId,
                             boolean isRedelivered, long deliveryTag,
                             String consumerTag) throws IOException {
        AMQP.Basic.Deliver deliver = connection.getCommandFactory().createBasicDeliverBody(consumerTag, deliveryTag, isRedelivered,
                message.getExchange(), message.getRoutingKey());
        return writeMessage(message, (Method) deliver, channelId);

    }

    public long writeGetOk(final AmqpMessage message, int channelId,
                           boolean isRedelivered, long deliveryTag, int messageCount) throws IOException {

        AMQP.Basic.GetOk getOk = connection.getCommandFactory().createBasicGetOkBody(deliveryTag, isRedelivered,
                message.getExchange(), message.getRoutingKey(), messageCount);
        return writeMessage(message, (Method) getOk, channelId);
    }

    private long writeMessage(final AmqpMessage message, final Method method, int channelId) throws IOException {
        CompositeMessageBlock messageFrame = new CompositeMessageBlock();
        byte[] body = message.getContentBody();
        int bodyLength = body.length;
        Frame headerFrame = message.getContentHeader().toFrame(channelId, bodyLength);

        int frameMax = connection.getMaxFrameSize();
        boolean cappedFrameMax = frameMax > 0;
        int bodyPayloadMax = cappedFrameMax ? frameMax - AMQPFrame.NON_BODY_SIZE : bodyLength;

        if (cappedFrameMax && headerFrame.size() > frameMax) {
            String msg = String.format("Content headers exceeded max frame size: %d > %d", headerFrame.size(), frameMax);
            throw new IllegalArgumentException(msg);
        }
        messageFrame.setMethod(AMQPFrame.get(method.toFrame(channelId)));
        messageFrame.setHeader(AMQPFrame.get(headerFrame));

        for (int offset = 0; offset < body.length; offset += bodyPayloadMax) {
            int remaining = body.length - offset;

            int fragmentLength = (remaining < bodyPayloadMax) ? remaining
                    : bodyPayloadMax;
            Frame frame = Frame.fromBodyFragment(channelId, body,
                    offset, fragmentLength);
            messageFrame.addContent(AMQPFrame.get(frame));
        }
        connection.writeFrame(messageFrame);
        return body.length;
    }

    public void writeReturn(final AmqpMessage message, int channelId, int replyCode,
                            String replyText) throws IOException {

        AMQP.Basic.Return returnBody = connection.getCommandFactory().createBasicReturnBody(replyCode, replyText, message.getExchange(), message.getRoutingKey());

        writeMessage(message, (Method) returnBody, channelId);
    }


    public class CompositeMessageBlock implements AMQData {

        AMQPFrame method;

        AMQPFrame header;

        List<AMQPFrame> contents;

        @Override
        public void encode(ByteBuf buf) {
            method.encode(buf);
            header.encode(buf);
            contents.forEach(content -> {
                content.encode(buf);
            });
        }

        public CompositeMessageBlock() {
        }

        public CompositeMessageBlock(AMQPFrame method, AMQPFrame header,
                                     List<AMQPFrame> contents) {
            this.method = method;
            this.header = header;
            this.contents = contents;
        }

        public AMQPFrame getMethod() {
            return method;
        }

        public void setMethod(AMQPFrame method) {
            this.method = method;
        }

        public AMQPFrame getHeader() {
            return header;
        }

        public void setHeader(AMQPFrame header) {
            this.header = header;
        }

        public List<AMQPFrame> getContents() {
            return contents;
        }

        public void setContents(List<AMQPFrame> contents) {
            this.contents = contents;
        }

        public void addContent(AMQPFrame content) {
            if (this.contents == null) {
                this.contents = new ArrayList<>(2);
            }
            contents.add(content);
        }


    }
}
