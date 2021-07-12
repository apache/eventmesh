package org.apache.eventmesh.runtime.admin.handler;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ServiceLoader;

import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.runtime.util.NetUtils;
import org.apache.eventmesh.store.api.openschema.common.ServiceException;
import org.apache.eventmesh.store.api.openschema.request.SubjectCreateRequest;
import org.apache.eventmesh.store.api.openschema.response.SubjectResponse;
import org.apache.eventmesh.store.api.openschema.service.SchemaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.JsonInclude;

public class CreateSubjectHandler implements HttpHandler {
	
	private Logger logger = LoggerFactory.getLogger(CreateSubjectHandler.class);
	
	private static ObjectMapper jsonMapper;
	
	static {
        jsonMapper = new ObjectMapper();        
        jsonMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        jsonMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        jsonMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);          
    }
	
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String result = "";
        OutputStream out = httpExchange.getResponseBody();
        try {
            String params = NetUtils.parsePostBody(httpExchange);                                
            SubjectCreateRequest subjectCreateRequest = jsonMapper.readValue(params, SubjectCreateRequest.class);                
            String subject = subjectCreateRequest.getSubject();
            
            if (StringUtils.isBlank(subject)) {
                result = "Create subject failed. Parameter subject not found.";
                logger.error(result);
                out.write(result.getBytes());
                return;
            }
            SchemaService schemaService = getSchemaService();
            SubjectResponse subjectResponse = schemaService.createSubject(subjectCreateRequest);
            if (subjectResponse != null) {
                logger.info("createTopic subject: {}", subject);                      
                httpExchange.getResponseHeaders().add("Content-Type", "appication/json");
                httpExchange.sendResponseHeaders(200, 0);
                result = jsonMapper.writeValueAsString(subjectResponse);                
                logger.info(result);
                out.write(result.getBytes());
                return;
            } else {
                httpExchange.sendResponseHeaders(500, 0);
                result = String.format("create subject failed! Server side error");
                logger.error(result);
                out.write(result.getBytes());
                return;
            }
        } catch (ServiceException e) {            	
        	httpExchange.getResponseHeaders().add("Content-Type", "appication/json");
            httpExchange.sendResponseHeaders(500, 0);                            
            result = jsonMapper.writeValueAsString(e.getErrorResponse());
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
    
    private SchemaService getSchemaService() {
        ServiceLoader<SchemaService> schemaServiceLoader = ServiceLoader.load(SchemaService.class);
        if (schemaServiceLoader.iterator().hasNext()) {
        	return schemaServiceLoader.iterator().next();
        }
        return null;
    }
    
}