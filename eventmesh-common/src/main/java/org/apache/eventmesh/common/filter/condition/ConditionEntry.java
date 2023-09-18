package org.apache.eventmesh.common.filter.condition;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;


public class ConditionEntry {
    private String field;
    private String path;
    private List<Condition> conditionList = new ArrayList<>();

    public ConditionEntry(String field, String path){
        this.field = field;
        this.path = path;
    }

    public void addCondition(Condition condition){
        this.conditionList.add(condition);
    }



}
