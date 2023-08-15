package org.apache.eventmesh.common.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.utils.JsonPathUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class JsonPathParser implements Parser{

    protected List<Variable> variablesList = null;

    public List<Variable> getVariablesList() {
        return variablesList;
    }

    /**
     * transform input jsonpath string into variable list
     * @param jsonPathString
     */
    public JsonPathParser(String jsonPathString){
        List<Variable> variableList = new ArrayList<>();
        JsonNode jsonObject = JsonPathUtils.parseStrict(jsonPathString);
        Iterator<Map.Entry<String, JsonNode>> fields = jsonObject.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String name = entry.getKey();
            JsonNode valueNode = entry.getValue();
            if(valueNode.isValueNode()){
                variableList.add(new Variable(name, valueNode.asText()));
            }else{
                throw new EventMeshException("invalid config:"+jsonPathString);
            }

        }

        this.variablesList = variableList;
    }





    /**
     * use jsonpath to match json and return result
     * @param json
     * @return
     */

    @Override
    public List<Variable> process(String json) {

        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }

        List<Variable> variableList = new ArrayList<>(variablesList.size());
        for (Variable element : variablesList) {
            if (JsonPathUtils.isValidAndDefinite(element.getJsonPath())) {
                variableList.add(new Variable(element.getName(),
                            JsonPathUtils.matchJsonPathValue(json, element.getJsonPath())));
            } else {
                variableList.add(element);
            }

        }
        return variableList;
    }
}
