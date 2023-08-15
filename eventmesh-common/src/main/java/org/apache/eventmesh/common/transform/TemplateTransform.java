package org.apache.eventmesh.common.transform;

import lombok.AllArgsConstructor;
import org.apache.eventmesh.common.exception.EventMeshException;

import java.util.List;

@AllArgsConstructor
public class TemplateTransform implements Transform{

    private Parser jsonPathParser;

    private Template template;

    @Override
    public String process(String json) throws EventMeshException {
        List<Variable> variableList = match(json, jsonPathParser);
        String res = transform(variableList, template);
        return res;
    }

}
