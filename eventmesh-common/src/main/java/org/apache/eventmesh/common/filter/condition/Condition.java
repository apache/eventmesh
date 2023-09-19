package org.apache.eventmesh.common.filter.condition;

import com.fasterxml.jackson.databind.JsonNode;


/**
 *
 */
public interface Condition {

    boolean match(JsonNode inputEvent);

}


