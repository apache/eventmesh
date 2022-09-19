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
package org.apache.eventmesh.runtime.core.protocol.amqp.remoting.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.AMQData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@ChannelHandler.Sharable
public class AmqpCodeEncoder extends MessageToByteEncoder<Object> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());


    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out)
        throws Exception {
        try {
            if (msg instanceof AMQData) {
                ((AMQData) msg).encode(out);
            }
        } catch (Exception e) {
            logger.error("channel {} encode exception", ctx.channel().toString(), e);
            ctx.close();
        }
    }

}
