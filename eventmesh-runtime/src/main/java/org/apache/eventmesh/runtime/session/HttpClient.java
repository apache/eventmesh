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
package org.apache.eventmesh.runtime.session;

import java.net.InetSocketAddress;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.DecoderResultProvider;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.timeout.IdleStateHandler;

public class HttpClient {


	private EventLoopGroup workerGroup = new NioEventLoopGroup();

	private Bootstrap bootstrap = new Bootstrap();
	
	private Map<ChannelId , DownstreamHandler> channelIdMap = new ConcurrentHashMap<>();
	
	

	public void init( ) throws CertificateException, SSLException {
		bootstrap.group(workerGroup);
		bootstrap.channel(NioSocketChannel.class);
		bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
		bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
		bootstrap.option(ChannelOption.TCP_NODELAY, true);
		bootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
		bootstrap.handler(new ChannelInitializer<SocketChannel>() {
			@Override
			public void initChannel(SocketChannel ch) throws Exception {
				ch.pipeline().addLast(new IdleStateHandler(500,500,500, TimeUnit.MILLISECONDS));
				ch.pipeline().addLast(new HttpResponseDecoder());
				ch.pipeline().addLast(new HttpRequestEncoder());
				ch.pipeline().addLast(new HttpClientHandler());
			}
		});
	}
	

	public Channel connect(InetSocketAddress inetSocketAddress,DownstreamHandler downstreamHandler ) throws InterruptedException {
		Channel channel = bootstrap.bind(inetSocketAddress).sync().channel();
		channelIdMap.put(channel.id(), downstreamHandler);
		return channel;
	}
	
	class HttpClientHandler extends ChannelInboundHandlerAdapter {

		private DefaultHttpResponse defaultHttpResponse;

		private ByteBuf connect;


		@Override
		public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
		}

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			if (msg instanceof DecoderResultProvider) {
				DecoderResultProvider decoderResultProvider = (DecoderResultProvider) msg;
				DecoderResult decoderResult = decoderResultProvider.decoderResult();
				if (Objects.nonNull(decoderResult) && !decoderResult.isSuccess()) {
					ctx.close();
					DownstreamHandler downstreamHandler = HttpClient.this.channelIdMap.remove(ctx.channel().id());
					downstreamHandler.exception(decoderResult.cause());
				}
			}
			if (msg instanceof DefaultHttpResponse) {
				defaultHttpResponse = (DefaultHttpResponse) msg;
				HttpHeaders headers = defaultHttpResponse.headers();
				Integer contentLength = headers.getInt(HttpHeaderNames.CONTENT_LENGTH);
				connect = Objects.isNull(contentLength) ? Unpooled.buffer(8192) : Unpooled.buffer(contentLength);
			}

			if (msg instanceof LastHttpContent) {
				LastHttpContent lastHttpContent = (LastHttpContent) msg;
				connect.writeBytes(lastHttpContent.content());
				
				
			}
			if (msg instanceof HttpContent) {
				HttpContent content = (HttpContent) msg;
				if(Objects.nonNull(connect)) {
					connect.writeBytes(content.content());
				}
			}
		}


		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			ctx.close();
			DownstreamHandler downstreamHandler = HttpClient.this.channelIdMap.remove(ctx.channel().id());
			downstreamHandler.exception(cause);
		}

	}

	
	
}
