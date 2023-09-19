package org.apache.eventmesh.common.filter;


import org.apache.eventmesh.common.filter.condition.Condition;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public class PatternEntry {


    private final String patternPath;

    private final List<Condition> conditionList = new ArrayList<>();

    public PatternEntry(final String patternPath) {
        this.patternPath = patternPath;
    }

    public void addRuleCondition(Condition patternCondition) {
        this.conditionList.add(patternCondition);
    }


    public String getPatternName() {
        return "123";
    }

    public String getPatternPath() {
        return patternPath;
    }

    public boolean match(JsonNode jsonElement) {
        for (final Condition patternCondition : conditionList) {
            if (patternCondition.match(jsonElement)) {
                return true;
            }
        }

        return false;

    }

    /**
     * Returns the condition list for test only
     *
     * @return the condition list
     */
    List<Condition> getConditionList() {
        return conditionList;
    }
}
