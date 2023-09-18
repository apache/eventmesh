package org.apache.eventmesh.common.filter.condition;

import com.fasterxml.jackson.databind.JsonNode;
import org.checkerframework.checker.units.qual.C;

public class ConditionsBuilder {

    private String key;
    private JsonNode jsonNode;
    public ConditionsBuilder withKey(String key) {
        this.key = key;
        return this;
    }

    public ConditionsBuilder withParams(JsonNode jsonNode) {
        this.jsonNode = jsonNode;
        return this;
    }

    public Condition build() {
        Condition condition = null;
        switch (this.key) {
            case "prefix":
                condition = new PrefixxCondition(this.jsonNode);
                break;
            case "suffix":
                condition = new SuffixCondition(this.jsonNode);
                break;
            case "anything-but":
                condition = new AnythingButCondition(this.jsonNode);
                break;
            case "numeric":
                condition = new NumericCondition(this.jsonNode);
                break;
            case "exists":
                condition = new ExistsCondition(this.jsonNode);
                break;
            case "specified":
                condition = new SpecifiedCondition(this.jsonNode);
                break;
            default:
                throw new RuntimeException("INVALID CONDITION");
            // Add cases for other keys and conditions
        }
        if(condition == null){
            throw new RuntimeException("NO RIGHT KEY");
        }
        return condition;
    }
}
