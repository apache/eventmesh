package org.apache.eventmesh.common.filter.condition;

import com.fasterxml.jackson.databind.JsonNode;

class SpecifiedCondition implements Condition {

    private final JsonNode specified;

    SpecifiedCondition(JsonNode jsonNode) {
        specified = jsonNode;
    }

    @Override
    public boolean match(JsonNode inputEvent) {
        return inputEvent.asText().equals(specified.asText());
    }
}
