package org.apache.eventmesh.admin.rocketmq.handler;

import java.io.IOException;
import java.io.OutputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.admin.rocketmq.request.TopicCreateRequest;
import org.apache.eventmesh.admin.rocketmq.response.TopicResponse;
import org.apache.eventmesh.admin.rocketmq.util.JsonUtils;
import org.apache.eventmesh.admin.rocketmq.util.NetUtils;
import org.apache.eventmesh.admin.rocketmq.util.RequestMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class TopicsHandler implements HttpHandler {
	
	private static final Logger logger = LoggerFactory.getLogger(TopicsHandler.class);


    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
    	
    	// create a new topic
    	if (RequestMapping.postMapping("/clientmanage/topics", httpExchange)) {
    		createTopicHandler(httpExchange);
    		return;
    	}

    	
    	OutputStream out = httpExchange.getResponseBody();
    	httpExchange.sendResponseHeaders(500, 0);
        String result = String.format("Please check your request url which does not match any API!!!");
        logger.error(result);
        out.write(result.getBytes());
        return;       	
    }
    
    public void createTopicHandler(HttpExchange httpExchange) throws IOException {
        String result = "";
        OutputStream out = httpExchange.getResponseBody();
        try {
            String params = NetUtils.parsePostBody(httpExchange);
            TopicCreateRequest topicCreateRequest = JsonUtils.toObject(params, TopicCreateRequest.class);
            String topic = topicCreateRequest.getName();
            
            if (StringUtils.isBlank(topic)) {
                result = "Create topic failed. Parameter topic not found.";
                logger.error(result);
                out.write(result.getBytes());
                return;
            }
            
            //TBD: A new rocketmq service will be implemented for creating topics
            TopicResponse topicResponse = null;
            if (topicResponse != null) {
                logger.info("create a new topic: {}", topic);                      
                httpExchange.getResponseHeaders().add("Content-Type", "appication/json");
                httpExchange.sendResponseHeaders(200, 0);
                result = JsonUtils.toJson(topicResponse);                
                logger.info(result);
                out.write(result.getBytes());
                return;
            } else {
                httpExchange.sendResponseHeaders(500, 0);
                result = String.format("create topic failed! Server side error");
                logger.error(result);
                out.write(result.getBytes());
                return;
            }
        } catch (Exception e) {            	
        	httpExchange.getResponseHeaders().add("Content-Type", "appication/json");
            httpExchange.sendResponseHeaders(500, 0);                            
            result = String.format("create topic failed! Server side error");
            logger.error(result);
            out.write(result.getBytes());
            return;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    logger.warn("out close failed...", e);
                }
            }
        }
    }
    
}
