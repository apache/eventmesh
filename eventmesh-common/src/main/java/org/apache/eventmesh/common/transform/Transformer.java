package org.apache.eventmesh.common.transform;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.util.NettyRuntime;
import org.apache.commons.text.StringSubstitutor;
import org.apache.eventmesh.common.exception.EventMeshException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface Transformer {
    String transform(String json) throws JsonProcessingException;

}

class ConstantTransformer implements Transformer{

    private final String jsonpath;
    ConstantTransformer(String jsonpath){
        this.jsonpath = jsonpath;
    }

    @Override
    public String transform(String json) throws EventMeshException {
        return this.jsonpath;
    }
}

class OriginalTransformer implements Transformer{

    @Override
    public String transform(String json) {
        return json;
    }
}


class  TemplateTransformer implements Transformer{

    private final JsonPathParser jsonPathParser;

    private final Template template;


    TemplateTransformer(JsonPathParser jsonPathParser, Template template){
        this.template = template;
        this.jsonPathParser = jsonPathParser;
    }

    @Override
    public String transform(String json) throws JsonProcessingException {
        //1: get variable match results
        List<Variable> variableList = jsonPathParser.match(json);
        //2: use results replace template
        String res = template.substitute(variableList);
        return res;
    }

}
