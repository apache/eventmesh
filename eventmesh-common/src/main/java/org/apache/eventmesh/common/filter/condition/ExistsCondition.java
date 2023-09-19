package org.apache.eventmesh.common.filter.condition;

import com.fasterxml.jackson.databind.JsonNode;

class ExistsCondition implements Condition {

    private JsonNode exists;

    ExistsCondition(JsonNode exists) {
        this.exists = exists;
    }

    @Override
    public boolean match(JsonNode inputEvent) {

        return this.exists.asBoolean() == (inputEvent != null);
    }
}
