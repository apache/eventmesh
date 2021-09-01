package org.apache.eventmesh.schema.rest.handler;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import org.apache.eventmesh.schema.rest.util.RequestMapping;
import org.apache.eventmesh.schema.rest.util.UrlMappingPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class SubjectsHandler implements HttpHandler {
	
	private static final Logger logger = LoggerFactory.getLogger(SubjectsHandler.class);
	
	private static ObjectMapper jsonMapper;  

    static {
        jsonMapper = new ObjectMapper();        
        jsonMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        jsonMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        jsonMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);          
    }
    
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
    	
    	String requestURI = httpExchange.getRequestURI().getPath();    	
    	if (RequestMapping.postMapping("/schemaregistry/subjects/{subject}", httpExchange)) {			
    		createSubjectHandler(httpExchange);
    		return;
    	}
    	
    	//æ£€æŸ¥ã€�æ³¨å†ŒSchema
    	if (RequestMapping.postMapping("/schemaregistry/subjects/{subject}/versions", httpExchange)) {  
    		Map<String, String> parametersMap = new UrlMappingPattern("/schemaregistry/subjects/{subject}/versions").extractPathParameterValues(requestURI);
    		createSchemaHandler(httpExchange, parametersMap);
    		return;
    	}
    	
    	//èŽ·å�–æ‰€æœ‰çš„subject
    	if (RequestMapping.getMapping("/schemaregistry/subjects", httpExchange)) {        		
    		ShowAllSubjectNamesHandler(httpExchange);
    		return;
    	}
    	
    	//èŽ·å�–subjectå®šä¹‰
    	if (RequestMapping.getMapping("/schemaregistry/subjects/{subject}", httpExchange)) {
    		Map<String, String> parametersMap = new UrlMappingPattern("/schemaregistry/subjects/{subject}").extractPathParameterValues(requestURI);
    		readSubjectByNameHandler(httpExchange, parametersMap);
    		return;
    	}
    	
    	//èŽ·å�–å¯¹åº”subjectçš„æ‰€æœ‰ç‰ˆæœ¬
    	if (RequestMapping.getMapping("/schemaregistry/subjects/{subject}/versions", httpExchange)) {
    		Map<String, String> parametersMap = new UrlMappingPattern("/schemaregistry/subjects/{subject}/versions").extractPathParameterValues(requestURI);
    		showAllVersionsBySubjectHandler(httpExchange, parametersMap);
    		return;
    	}
    	
    	//æ ¹æ�®subjectä»¥å�Šschemaç‰ˆæœ¬èŽ·å�–schemaå®šä¹‰
    	if (RequestMapping.getMapping("/schemaregistry/subjects/{subject}/versions/{version}/schema", httpExchange)) {
    		Map<String, String> parametersMap = new UrlMappingPattern("/schemaregistry/subjects/{subject}/versions/{version}/schema").extractPathParameterValues(requestURI);
    		readSchemaBySubjectAndVersionHandler(httpExchange, parametersMap);
    		return;
    	}
    	
    	//åˆ é™¤subjectä»¥å�Šå…¶å¯¹åº”çš„å…¼å®¹æ€§è®¾ç½®
    	if (RequestMapping.deleteMapping("/schemaregistry/subjects/{subject}", httpExchange)) {        		
    		Map<String, String> parametersMap = new UrlMappingPattern("/schemaregistry/subjects/{subject}").extractPathParameterValues(requestURI);
    		deleteSubjectByNameHandler(httpExchange, parametersMap);
    		return;
    	}
    	
    	//åˆ é™¤æŒ‡å®šsubjectæŒ‡å®šç‰ˆæœ¬çš„schema
    	if (RequestMapping.deleteMapping("/schemaregistry/subjects/{subject}/versions/{version}", httpExchange)) {        		
    		Map<String, String> parametersMap = new UrlMappingPattern("/schemaregistry/subjects/{subject}/versions/{version}").extractPathParameterValues(requestURI);
    		deleteSchemaBySubjectAndVersionHandler(httpExchange, parametersMap);
    		return;
    	}
    	
    	OutputStream out = httpExchange.getResponseBody();
    	httpExchange.sendResponseHeaders(500, 0);
        String result = String.format("Please check your request url which does not match any API!!!");
        logger.error(result);
        out.write(result.getBytes());
        return;       	
    }
    
    public void createSubjectHandler(HttpExchange httpExchange) throws IOException {
        //TO Be Implemented, the following is a testing code
    	
    	String result = "Hello World!! POST Request Worked";
        OutputStream out = httpExchange.getResponseBody();
        try {            	        	
            httpExchange.sendResponseHeaders(200, 0);
        	out.write(result.getBytes());
            return;
        } catch (Exception e) {            	        	
            httpExchange.sendResponseHeaders(500, 0);                                
            result = jsonMapper.writeValueAsString(e.getMessage());
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

    public void createSchemaHandler(HttpExchange httpExchange, Map<String, String> parametersMap) throws IOException {
        //TO DO
    }
    
    public void ShowAllSubjectNamesHandler(HttpExchange httpExchange) throws IOException {
        //TO DO
    }        
    
    public void readSubjectByNameHandler(HttpExchange httpExchange, Map<String, String> parametersMap) throws IOException {
        //TO DO
    }            

    public void showAllVersionsBySubjectHandler(HttpExchange httpExchange, Map<String, String> parametersMap) throws IOException {
        //TO DO
    }            

    public void readSchemaBySubjectAndVersionHandler(HttpExchange httpExchange, Map<String, String> parametersMap) throws IOException {
        //TO DO
    }
    
    public void deleteSubjectByNameHandler(HttpExchange httpExchange, Map<String, String> parametersMap) throws IOException {
        //TO DO
    }
    
    public void deleteSchemaBySubjectAndVersionHandler(HttpExchange httpExchange, Map<String, String> parametersMap) throws IOException {
        //TO DO
    }
}
