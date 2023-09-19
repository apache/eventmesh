package org.apache.eventmesh.common.filter.condition;

import com.fasterxml.jackson.databind.JsonNode;

class PrefixxCondition implements Condition {

    private final String prefix;

    public PrefixxCondition(JsonNode suffix) {
        this.prefix = suffix.asText();
    }

    @Override
    public boolean match(JsonNode inputEvent) {
        return inputEvent.asText().startsWith(prefix);
    }
}
