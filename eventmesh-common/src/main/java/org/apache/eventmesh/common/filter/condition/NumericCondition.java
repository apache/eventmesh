package org.apache.eventmesh.common.filter.condition;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

class NumericCondition implements Condition {

    List<String> operators = new ArrayList<>();
    List<Double> nums = new ArrayList<>();

    NumericCondition(JsonNode numeric) {
        if (numeric.isArray()) {
            if (numeric.size() % 2 != 0) {
                throw new RuntimeException("NUMERIC NO RIGHT FORMAT");
            }
            for (int i = 0; i < numeric.size(); i += 2) {
                JsonNode opt = numeric.get(i);
                JsonNode number = numeric.get(i + 1);
                operators.add(opt.asText());
                nums.add(number.asDouble());
            }
        } else {
            throw new RuntimeException("NUMERIC MUST BE ARRAY");
        }
    }

    private boolean compareNums(Double rule, Double target, String opt) {
        int res = Double.compare(target, rule);
        switch (opt) {
            case "=":
                return res == 0;
            case "!=":
                return res != 0;
            case ">":
                return res > 0;
            case ">=":
                return res >= 0;
            case "<":
                return res < 0;
            case "<=":
                return res <= 0;
            default: // Never be here
                return false;
        }
    }

    @Override
    public boolean match(JsonNode inputEvent) {
        if (inputEvent.isNumber()) {
            for (int i = 0; i < operators.size(); ++i) {
                if (!compareNums(nums.get(i), inputEvent.asDouble(), operators.get(i))) {
                    return false;
                }

            }

        }

        return true;
    }
}
