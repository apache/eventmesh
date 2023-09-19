package org.apache.eventmesh.common.transform;


import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.utils.JsonPathUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

public class JsonPathParser {

    protected List<Variable> variablesList = new ArrayList<>();

    public List<Variable> getVariablesList() {
        return variablesList;
    }

    /**
     * parser input jsonpath string into variable list
     *
     * @param jsonPathString
     */
    public JsonPathParser(String jsonPathString) {
        JsonNode jsonObject = JsonPathUtils.parseStrict(jsonPathString);
        Iterator<Map.Entry<String, JsonNode>> fields = jsonObject.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String name = entry.getKey();
            JsonNode valueNode = entry.getValue();
            if (valueNode.isValueNode()) {
                variablesList.add(new Variable(name, valueNode.asText()));
            } else {
                throw new EventMeshException("invalid config:" + jsonPathString);
            }

        }

    }


    /**
     * use jsonpath to match json and return result
     *
     * @param json
     * @return
     */

    public List<Variable> match(String json) throws JsonProcessingException {

        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }

        List<Variable> variableList = new ArrayList<>(variablesList.size());
        for (Variable element : variablesList) {
            if (JsonPathUtils.isValidAndDefinite(element.getJsonPath())) {
                String res = JsonPathUtils.matchJsonPathValueWithString(json, element.getJsonPath());
                Variable variable = new Variable(element.getName(), res);
                variableList.add(variable);
            } else {
                variableList.add(element);
            }

        }
        return variableList;
    }
}
