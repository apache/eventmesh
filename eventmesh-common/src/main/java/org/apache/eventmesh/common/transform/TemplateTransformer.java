package org.apache.eventmesh.common.transform;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;

class TemplateTransformer implements Transformer {

    private final JsonPathParser jsonPathParser;

    private final Template template;


    TemplateTransformer(JsonPathParser jsonPathParser, Template template) {
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
