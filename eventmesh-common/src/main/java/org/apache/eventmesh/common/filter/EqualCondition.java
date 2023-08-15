package org.apache.eventmesh.common.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

public class EqualCondition extends AbstractRuleCondition{

    private final JsonNode val;

    public EqualCondition(final JsonNode val) {
        this.val = val;
    }

    @Override
    boolean matchPrimitive(final JsonNode jsonPrimitive) {

        if (val instanceof NullNode) {
            return false;
        }


        return val.asText().equals(jsonPrimitive.asText());
    }

    @Override
    boolean matchNull(final JsonNode jsonNull) {
        return val.equals(jsonNull);
    }
}








