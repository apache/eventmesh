package org.apache.eventmesh.common.filter.condition;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.beans.binding.DoubleExpression;
import org.apache.eventmesh.common.utils.JsonPathUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public interface Condition {
    boolean match(JsonNode inputEvent);

}



class NumericCondition implements Condition{

    List<String> operators = new ArrayList<>();
    List<Double> nums = new ArrayList<>();

    NumericCondition(JsonNode numeric){
        if(numeric.isArray()){
            if(numeric.size() % 2 != 0){
                throw new RuntimeException("NUMERIC NO RIGHT FORMAT");
            }
            for (int i=0;i<numeric.size();i+=2){
                JsonNode opt = numeric.get(i);
                JsonNode number = numeric.get(i+1);
                operators.add(opt.asText());
                nums.add(number.asDouble());
            }
        }else{
            throw new RuntimeException("NUMERIC MUST BE ARRAY");
        }
    }

    private boolean compareNums(Double rule, Double target, String opt){
        int res = Double.compare(target, rule);
        switch (opt){
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
        if (inputEvent.isNumber()){
            for(int i = 0; i < operators.size(); ++i){
                if( !compareNums(nums.get(i), inputEvent.asDouble(),operators.get(i)) ){
                    return false;
                }

            }

        }

        return true;
    }
}

class AnythingButCondition implements Condition{
    private List<Condition> conditionList = new ArrayList<>();

     AnythingButCondition(JsonNode condition){


         if(condition.isValueNode()){
             this.conditionList.add(new SpecifiedCondition(condition));
         }
         //[]
         if(condition.isArray()){
            for(JsonNode element: condition){
                this.conditionList.add(new SpecifiedCondition(element));
            }
         }

         //prefix,suffix
         if(condition.isObject()){
             Iterator<Map.Entry<String, JsonNode>> iterator = condition.fields();
             while(iterator.hasNext()){
                 Map.Entry<String, JsonNode> entry = iterator.next();
                 String key = entry.getKey();
                 JsonNode value = entry.getValue();
                 Condition condition1 =  new ConditionsBuilder().withKey(key).withParams(value).build();
                 this.conditionList.add(condition1);
             }
         }

     }

    @Override
    public boolean match(JsonNode inputEvent) {
         if(inputEvent == null) return false;

         for(Condition condition : conditionList){
             if(condition.match(inputEvent)){
                 return false;
             }
         }
        return true;
    }

}


class SpecifiedCondition implements Condition{
    private final JsonNode specified;

    SpecifiedCondition(JsonNode jsonNode){
        specified = jsonNode;
    }

    @Override
    public boolean match(JsonNode inputEvent) {
       return inputEvent.asText().equals(specified.asText());
    }
}



class ExistsCondition implements Condition{
    private JsonNode exists;

    ExistsCondition(JsonNode exists){
        this.exists = exists;
    }

    @Override
    public boolean match(JsonNode inputEvent) {

        return this.exists.asBoolean() == (inputEvent != null);
    }
}


class PrefixxCondition implements Condition{
    private final String prefix;
    public PrefixxCondition(JsonNode suffix){
        this.prefix = suffix.asText();
    }

    @Override
    public boolean match(JsonNode inputEvent) {
        return inputEvent.asText().startsWith(prefix);
    }
}


class SuffixCondition implements Condition{
    private final String suffix;
    public SuffixCondition(JsonNode suffix){
        this.suffix = suffix.asText();
    }

    @Override
    public boolean match(JsonNode inputEvent) {
        return inputEvent.asText().endsWith(this.suffix);
    }
}
