package org.apache.eventmesh.common.filter.condition;

import com.fasterxml.jackson.databind.JsonNode;

class SuffixCondition implements Condition {

    private final String suffix;

    public SuffixCondition(JsonNode suffix) {
        this.suffix = suffix.asText();
    }

    @Override
    public boolean match(JsonNode inputEvent) {
        return inputEvent.asText().endsWith(this.suffix);
    }
}
