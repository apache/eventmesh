package org.apache.eventmesh.common.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import org.omg.CORBA.PUBLIC_MEMBER;

public class JacksonUtils {

    private static final ObjectMapper OBJET_MAPPER = new ObjectMapper();


    private static final Configuration JSON_PATH_CONFIG = Configuration.builder()
            .jsonProvider(new JacksonJsonProvider(OBJET_MAPPER))
            .build();

    public static JsonNode STRING_TO_JSONNODE(String data){
        JsonNode jsonNode = null;
        try{
            jsonNode = OBJET_MAPPER.readTree(data);
        }catch (Exception e){
            throw new RuntimeException("INVALID_JSON_STRING",e);
        }

        return jsonNode;
    }


}
