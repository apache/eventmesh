package org.apache.eventmesh.common.filter;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public class PatternEntry {

    private String patternName;


    private String patternPath;


    private PatternType patternType = PatternType.OR;

    private List<RuleCondition> conditionList = new ArrayList<>();

    public PatternEntry(final String patternName, final String patternPath) {
        this.patternName = patternName;
        this.patternPath = patternPath;
    }

    public void addRuleCondition(RuleCondition patternCondition) {
        this.conditionList.add(patternCondition);
    }

    public String getPatternName() {
        return patternName;
    }

    public String getPatternPath() {
        return patternPath;
    }

    public boolean match(JsonNode jsonElement) {
        if (patternType == PatternType.OR) {
            for (final RuleCondition patternCondition : conditionList) {
                if (patternCondition.match(jsonElement)) {
                    return true;
                }
            }

            return false;
        }

        if (patternType == PatternType.AND) {
            for (final RuleCondition patternCondition : conditionList) {
                if (!patternCondition.match(jsonElement)) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    /**
     * Returns the condition list for test only
     *
     * @return the condition list
     */
    List<RuleCondition> getConditionList() {
        return conditionList;
    }
}
