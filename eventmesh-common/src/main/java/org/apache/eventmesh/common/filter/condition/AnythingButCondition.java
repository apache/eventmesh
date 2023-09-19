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
