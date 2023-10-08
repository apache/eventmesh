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

package org.apache.eventmesh.common.filter.condition;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;

class AnythingButCondition implements Condition {

    private List<Condition> conditionList = new ArrayList<>();

    AnythingButCondition(JsonNode condition) {

        if (condition.isValueNode()) {
            this.conditionList.add(new SpecifiedCondition(condition));
        }
        //[]
        if (condition.isArray()) {
            for (JsonNode element : condition) {
                this.conditionList.add(new SpecifiedCondition(element));
            }
        }

        //prefix,suffix
        if (condition.isObject()) {
            Iterator<Entry<String, JsonNode>> iterator = condition.fields();
            while (iterator.hasNext()) {
                Entry<String, JsonNode> entry = iterator.next();
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                Condition condition1 = new ConditionsBuilder().withKey(key).withParams(value).build();
                this.conditionList.add(condition1);
            }
        }

    }

    @Override
    public boolean match(JsonNode inputEvent) {
        if (inputEvent == null) {
            return false;
        }

        for (Condition condition : conditionList) {
            if (condition.match(inputEvent)) {
                return false;
            }
        }
        return true;
    }

}
