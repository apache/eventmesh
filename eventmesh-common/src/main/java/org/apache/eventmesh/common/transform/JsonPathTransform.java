package org.apache.eventmesh.common.transform;

import org.apache.eventmesh.common.exception.EventMeshException;

import java.util.List;

public class JsonPathTransform implements Transform {

    private Parser jsonPathParser;

    public JsonPathTransform(Parser jsonPathParser) {
        this.jsonPathParser = jsonPathParser;
    }

    @Override
    public String process(String inputData) throws EventMeshException {
        List<Variable> variableList = match(inputData, jsonPathParser);
        if (variableList == null) {
            return "";
        }
        if (variableList.size() != 1) {
            throw new EventMeshException("InvalidConfig");
        }

        if (variableList.get(0).getJsonPath() == null) {
            return "";
        } else {
            return variableList.get(0).getJsonPath();
        }
    }


}
