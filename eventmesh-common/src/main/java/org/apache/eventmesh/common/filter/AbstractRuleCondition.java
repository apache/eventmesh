package org.apache.eventmesh.common.filter;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.net.util.SubnetUtils;


public abstract class AbstractRuleCondition implements RuleCondition {
    @Override
    public boolean match(JsonNode jsonData) {
        if (jsonData == null) {
            // Only ExistsCondition instance accept a null data
            return this instanceof ExistsCondition && this.matchPrimitive(null);
        }

        if (!check(jsonData)) {
            return false;
        }

        if (jsonData.isValueNode()) {
            return matchPrimitive(jsonData);
        }

        if (jsonData.isNull()) {
            return matchNull(jsonData);
        }

        // If the value in the event is an array, then the pattern matches if the intersection of the pattern array
        // and the event array is non-empty.
        if (jsonData.isArray()) {
            for (final JsonNode element : jsonData) {
                if (element.isValueNode() && matchPrimitive(element)) {
                    return true;
                }

                if (element.isNull() && matchNull(element)) {
                    return true;
                }
            }
        }

        return false;
    }

    boolean check(JsonNode jsonData) {
        if (jsonData.isObject()) {
            return false;
        }

        if (jsonData.isArray()) {
            for (final JsonNode element : jsonData) {
                if (!(element.isValueNode() || element.isNull())) {
                    return false;
                }
            }
        }

        return true;
    }

    abstract boolean matchPrimitive(JsonNode jsonNode);

    abstract boolean matchNull(JsonNode jsonNode);
}


class PrefixCondition extends AbstractRuleCondition {
    /**
     * Don't accept null string
     */
    private final String prefix;

    public PrefixCondition(final String prefix) {
        this.prefix = prefix;
    }

    @Override
    boolean matchPrimitive(final JsonNode jsonPrimitive) {
        return jsonPrimitive.isTextual() && jsonPrimitive.asText().startsWith(prefix);
    }

    @Override
    boolean matchNull(final JsonNode jsonNull) {
        return false;
    }
}

class SuffixCondition extends AbstractRuleCondition {
    /**
     * Don't accept null string
     */
    private final String suffix;

    public SuffixCondition(final String suffix) {
        this.suffix = suffix;
    }

    @Override
    boolean matchPrimitive(final JsonNode jsonPrimitive) {
        return jsonPrimitive.isTextual() && jsonPrimitive.asText().endsWith(suffix);
    }

    @Override
    boolean matchNull(final JsonNode jsonNull) {
        return false;
    }
}


class AnythingButCondition extends AbstractRuleCondition {
    private List<RuleCondition> anythingButs = new ArrayList<>();

    public void addRuleCondition(RuleCondition patternCondition) {
        anythingButs.add(patternCondition);
    }

    @Override
    boolean matchPrimitive(final JsonNode jsonPrimitive) {
        return matchJsonNode(jsonPrimitive);
    }

    @Override
    boolean matchNull(final JsonNode jsonNull) {
        return matchJsonNode(jsonNull);
    }

    private boolean matchJsonNode(final JsonNode jsonNode) {
        for (final RuleCondition condition : anythingButs) {
            if (condition.match(jsonNode)) {
                return false;
            }
        }
        return true;
    }
}

class NumericCondition extends AbstractRuleCondition {
    private List<Double> dataList = new ArrayList<>();
    private List<NumericOperators> operatorList = new ArrayList<>();

    public void addOperatorAndData(NumericOperators operator, double data) {
        dataList.add(data);
        operatorList.add(operator);
    }

    @Override
    boolean matchPrimitive(final JsonNode jsonPrimitive) {
        if (!jsonPrimitive.isNumber()) {
            return false;
        }

        for (int i = 0; i < operatorList.size(); i++) {
            if (!operatorList.get(i).match(jsonPrimitive.asDouble(), dataList.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    boolean matchNull(final JsonNode jsonNull) {
        return false;
    }
}


class CIDRCondition extends AbstractRuleCondition {
    private final String cidrExp;

    public CIDRCondition(final String cidrExp) {
        this.cidrExp = cidrExp;
    }

    private boolean matchIpAddress(final String data) {
        boolean flag;
        try {
            if (cidrExp.contains("/")) {
                SubnetUtils snu = new SubnetUtils(cidrExp);
                flag = snu.getInfo().isInRange(data);
            } else {
                flag = InetAddress.getByName(data).equals(InetAddress.getByName(cidrExp));
            }
        } catch (Exception e) {
            flag = false;

        }
        return flag;
    }

    @Override
    boolean matchPrimitive(final JsonNode jsonPrimitive) {
        return jsonPrimitive.isTextual() && matchIpAddress(jsonPrimitive.asText());
    }

    @Override
    boolean matchNull(final JsonNode jsonNull) {
        return false;
    }
}

class ExistsCondition extends AbstractRuleCondition {
    private final boolean exists;

    ExistsCondition(final boolean exists) {
        this.exists = exists;
    }

    @Override
    boolean matchPrimitive(final JsonNode jsonPrimitive) {
        return exists == (jsonPrimitive != null && !jsonPrimitive.isNull());
    }

    @Override
    boolean matchNull(final JsonNode jsonNull) {
        return exists == false;
    }
}
