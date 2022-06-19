package org.apache.eventmesh.runtime.core.protocol.http.processor;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

public interface HttpProcessor {

	/**
	 * 需要监听paht
	 * 
	 * @return
	 */
	public String[] paths();

	/**
	 * 如果匹配到路由，就会调用handler方法 保留HttpRequest与HttpResponse，是为了提供更加灵活的操作
	 * 
	 * @param httpRequest
	 * @return
	 */
	public HttpResponse handler(HttpRequest httpRequest);

}
