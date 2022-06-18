package org.apache.eventmesh.runtime.core.protocol.http.processor;

import io.netty.handler.codec.http.*;
import org.apache.eventmesh.webhook.receive.WebHookController;

import java.util.HashMap;
import java.util.Map;

public class WebHookProcessor implements HttpProcessor{

	private WebHookController webHookController;

	@Override
	public String[] paths() {

		return new String[]{"/webhook"};
	}

	@Override
	public HttpResponse handler(HttpRequest httpRequest) {

		try {
			Map<String, String> header = new HashMap<>();
			for (Map.Entry<String, String> entry : httpRequest.headers().entries()) {
				header.put(entry.getKey(), entry.getValue());
			}
			webHookController.execute(httpRequest.uri(), header, httpRequest.toString().getBytes());
		}catch(Exception e) {
			return new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
		}

		return new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
	}

}
