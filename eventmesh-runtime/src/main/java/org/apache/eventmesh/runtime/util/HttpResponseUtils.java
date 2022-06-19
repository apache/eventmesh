package org.apache.eventmesh.runtime.util;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

public class HttpResponseUtils {

	public final static  HttpResponse createSuccess() {
		return  new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK);
	}
	
	public final static  HttpResponse createNotFound() {
		return  new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.NOT_FOUND);
	}
	
	public final static  HttpResponse createInternalServerError() {
		return  new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.INTERNAL_SERVER_ERROR);
	}
	
	private final static ByteBuf crateByteBuf(ChannelHandlerContext ctx,String body) {
		byte[] bytes = body.getBytes();
		ByteBuf byteBuf = ctx.alloc().buffer(bytes.length);
		byteBuf.writeBytes(bytes);
		return byteBuf;
	}
	
	public final static  HttpResponse setResponseJsonBody(String body,ChannelHandlerContext ctx) {
		HttpHeaders responseHeaders = new DefaultHttpHeaders();
		responseHeaders.add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML);
		return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK,crateByteBuf(ctx,body),responseHeaders,responseHeaders);
		
	}
	
	public final static  HttpResponse setResponseTextBody(String body,ChannelHandlerContext ctx) {
		HttpHeaders responseHeaders = new DefaultHttpHeaders();
		responseHeaders.add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
		return  new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK,crateByteBuf(ctx,body),responseHeaders,responseHeaders);
	}
	
}
