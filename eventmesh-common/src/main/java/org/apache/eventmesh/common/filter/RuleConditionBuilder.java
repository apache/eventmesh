package org.apache.eventmesh.common.filter;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.eventmesh.common.exception.EventMeshException;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.Map;

public interface RuleConditionBuilder {
    RuleCondition build(final JsonNode jsonElement);
}


class PrefixConditionBuilder implements RuleConditionBuilder {
    @Override
    public RuleCondition build(final JsonNode jsonNode) {
        if (jsonNode.isTextual()) {
            String prefix = jsonNode.asText();
            if (prefix.isEmpty()) {
                throw new EventMeshException("Pattern Error: EMPTY PREFIX CONDITION");
            }
            return new PrefixCondition(prefix);
        }
        throw new EventMeshException("Pattern Error: INVALID PREFIX CONDITION");
    }
}

class SuffixConditionBuilder implements RuleConditionBuilder {
    @Override
    public RuleCondition build(final JsonNode jsonNode) {
        if (jsonNode.isTextual()) {
            String suffix = jsonNode.asText();
            if (suffix.isEmpty()) {
                throw new EventMeshException("Pattern Error: EMPTY SUFFIX CONDITION");
            }
            return new SuffixCondition(suffix);
        }
        throw new EventMeshException("Pattern Error: INVALID SUFFIX CONDITION");
    }
}

class AnythingButConditionBuilder implements RuleConditionBuilder {

    @Override
    public RuleCondition build(final JsonNode jsonNode) {
        AnythingButCondition butCondition = new AnythingButCondition();
        if (jsonNode.isValueNode()) {
            butCondition.addRuleCondition(new EqualCondition(jsonNode));
            return butCondition;
        }

        if (jsonNode.isObject()) {
            final Iterator<Map.Entry<String, JsonNode>> entries = jsonNode.fields();
            while (entries.hasNext()) {
                final Map.Entry<String, JsonNode> elementEntry = entries.next();
                String key = elementEntry.getKey();
                if (!(ComplexConditionBuilders.prefix.toString().equals(key) ||
                        ComplexConditionBuilders.suffix.toString().equals(key))) {
                    throw new EventMeshException("Pattern Error: INVALID NESTED ANYTHING BUT CONDITION");
                }
            }
            butCondition.addRuleCondition(FilterPatternBuilder.parseCondition(jsonNode));
            return butCondition;
        }

        if (jsonNode.isArray()) {
            for (final JsonNode element : jsonNode) {
                if (element.isValueNode()) {
                    butCondition.addRuleCondition(new EqualCondition(element));
                    continue;
                }
                throw new EventMeshException("Pattern Error: INVALID ANYTHING BUT CONDITION");

            }
            return butCondition;
        }

        // Can't be here, throw an exception
        throw new EventMeshException("Pattern Error: INVALID ANYTHING BUT CONDITION");

    }
}

class CIDRConditionBuilder implements RuleConditionBuilder {

    @Override
    public RuleCondition build(final JsonNode jsonNode) {
        if (jsonNode.isTextual()) {
            String cidrExp = jsonNode.asText();
            if (cidrExp.isEmpty()) {
                throw new EventMeshException("Pattern Error: INVALID CIDR CONDITION");
            }

            try {
                if (cidrExp.contains("/")) {
                    // Don't remove these unused variables to keep the side effects
                    SubnetUtils snu = new SubnetUtils(cidrExp);
                } else {
                    final InetAddress name = InetAddress.getByName(cidrExp);
                }
            } catch (Exception e) {
                throw new EventMeshException("Pattern Error: INVALID CIDR CONDITION");            }

            return new CIDRCondition(cidrExp);
        }
        throw new EventMeshException("Pattern Error: INVALID CIDR CONDITION");    }
}

class NumericConditionBuilder implements RuleConditionBuilder {
    public static final double MAX_VAL = 1.0E9;
    public static final double MIN_VAL = -1.0E9;

    @Override
    public RuleCondition build(final JsonNode jsonNode) {
        if (!jsonNode.isArray()) {
            throw new EventMeshException("Pattern Error: INVALID NUMERIC CONDITION");
        }

        final JsonNode numericArray = jsonNode;
        if (numericArray.size() != 2 && numericArray.size() != 4) {
            throw new EventMeshException("Pattern Error: ARRAY SHOULD HAVE 2 OR 4 ELE");
        }

        NumericCondition condition = new NumericCondition();

        final Iterator<JsonNode> iterator = numericArray.iterator();
        while (iterator.hasNext()) {
            final JsonNode operator = iterator.next();
            final JsonNode data = iterator.next();
            if (operator.isTextual() && data.isNumber()) {
                final double num = data.asDouble();
                if (Double.compare(num, MAX_VAL) > 0 || Double.compare(num, MIN_VAL) < 0) {
                    throw new EventMeshException("Pattern Error: INVALID NUMERIC CONDITION CONDITION");
                }

                final NumericOperators operatorExp = NumericOperators.getOperatorByExp(operator.asText());
                if (operatorExp == null) {
                    throw new EventMeshException("Pattern Error: INVALID NUMERIC CONDITION CONDITION");
                }
                condition.addOperatorAndData(operatorExp, num);
                continue;
            }
            throw new EventMeshException("Pattern Error: INVALID NUMERIC CONDITION CONDITION");
        }

        return condition;
    }
}

class ExistsConditionBuilder implements RuleConditionBuilder {

    @Override
    public RuleCondition build(final JsonNode jsonNode) {
        if (jsonNode.isBoolean()) {
            return new ExistsCondition(jsonNode.asBoolean());
        }
        throw new EventMeshException("Pattern Error: INVALID EXISTS CONDITION");

    }
}

