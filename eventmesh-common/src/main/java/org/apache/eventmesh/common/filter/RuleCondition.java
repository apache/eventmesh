package org.apache.eventmesh.common.filter;

import com.fasterxml.jackson.databind.JsonNode;

public interface RuleCondition {
    boolean match(JsonNode jsonData);
}
