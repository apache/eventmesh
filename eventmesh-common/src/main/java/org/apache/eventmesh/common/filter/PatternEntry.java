package org.apache.eventmesh.common.filter;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.eventmesh.common.filter.condition.Condition;

import java.util.ArrayList;
import java.util.List;

public class PatternEntry {


    private String patternPath;

    private PatternType patternType = PatternType.OR;

    private List<Condition> conditionList = new ArrayList<>();

    public PatternEntry(final String patternPath) {
        this.patternPath = patternPath;
    }

    public void addRuleCondition(Condition patternCondition) {
        this.conditionList.add(patternCondition);
    }


    public String getPatternName(){
        return "123";
    }

    public String getPatternPath() {
        return patternPath;
    }

    public boolean match(JsonNode  jsonElement) {
        if (patternType == PatternType.OR) {
            for (final Condition patternCondition : conditionList) {
                if (patternCondition.match(jsonElement)) {
                    return true;
                }
            }

            return false;
        }

        if (patternType == PatternType.AND) {
            for (final Condition patternCondition : conditionList) {
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
    List<Condition> getConditionList() {
        return conditionList;
    }
}
