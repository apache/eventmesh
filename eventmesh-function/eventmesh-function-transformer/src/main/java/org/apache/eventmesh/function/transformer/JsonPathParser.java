/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.function.transformer;

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
                throw new TransformException("invalid config:" + jsonPathString);
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
            if (JsonPathUtils.isValidAndDefinite(element.getValue())) {
                String res = JsonPathUtils.matchJsonPathValueWithString(json, element.getValue());
                Variable variable = new Variable(element.getName(), res);
                variableList.add(variable);
            } else {
                variableList.add(element);
            }

        }
        return variableList;
    }
}
