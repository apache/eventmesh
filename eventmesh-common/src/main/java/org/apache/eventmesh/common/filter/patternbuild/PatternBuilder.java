package org.apache.eventmesh.common.filter.patternbuild;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.eventmesh.common.exception.JsonException;
import org.apache.eventmesh.common.filter.PatternEntry;
import org.apache.eventmesh.common.filter.condition.Condition;
import org.apache.eventmesh.common.filter.condition.ConditionsBuilder;
import org.apache.eventmesh.common.filter.pattern.Pattern;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Iterator;
import java.util.Queue;

import io.cloudevents.SpecVersion;

public class PatternBuilder {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static Pattern build(String jsonStr){

        Pattern pattern = new Pattern();
        JsonNode jsonNode = null;
        try {
            jsonNode = mapper.readTree(jsonStr);
        } catch (Exception e) {
            throw new JsonException("INVALID_JSON_STRING",e);
        }

        if (jsonNode.isEmpty() || !jsonNode.isObject()){
            return null;
        }

        //iter all json data
        Iterator<Map.Entry<String, JsonNode>> iterator = jsonNode.fields();
        while(iterator.hasNext()){
            Map.Entry<String, JsonNode> entry = iterator.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();

            if(key.equals("data")){

                parseDataField(value,pattern);
                continue;
            }

            if(!value.isArray()){
                throw new JsonException("INVALID_JSON_STRING");
            }

            if(!SpecVersion.V1.getAllAttributes().contains(key)){
                throw new JsonException("INVALID_JSON_KEY");
            }

            //iter all requiredField
            parseRequiredField("$."+key,value,pattern);
            if (value.isEmpty()) {
                // Empty array
                throw new JsonException("INVALID_PATTERN_VALUE ");
            }
            PatternEntry patternEntry = new PatternEntry("$."+key);
            for(final JsonNode objNode: value ){
                //{
                //            "suffix":".jpg"
                //        }
                Condition condition =  parseCondition(objNode);
                patternEntry.addRuleCondition(condition);
            }

            pattern.addRequiredFieldList(patternEntry);
        }


          return pattern;

    }

    private static void parseDataField(JsonNode jsonNode, Pattern pattern){
        if(!jsonNode.isObject()){
            throw new JsonException("INVALID_JSON_KEY");
        }
        Queue<Node> queueNode = new ArrayDeque<>();
        Node node = new Node("$.data", "data",jsonNode);
        queueNode.add(node);
        while(!queueNode.isEmpty()){
            Node ele = queueNode.poll();
            String elepath = ele.getPath();
            Iterator<Map.Entry<String, JsonNode>> iterator = ele.getValue().fields();
            while(iterator.hasNext()){
                Map.Entry<String, JsonNode> entry = iterator.next();
                //state
                String key = entry.getKey();
                //[{"anything-but":"initializing"}]  [{"anything-but":123}]}
                JsonNode value = entry.getValue();
                PatternEntry patternEntry = new PatternEntry(elepath+"."+key);
                if(!value.isObject()){
                    if(value.isArray()){
                        for(JsonNode node11 : value){
                            //{"anything-but":"initializing"}
                            patternEntry.addRuleCondition(parseCondition(node11));
                        }
                    }
                    pattern.addDataList(patternEntry);
                }else{
                    queueNode.add(new Node(elepath+"."+key,key,value));
                }
            }
        }

//        Iterator<Map.Entry<String, JsonNode>> iterator = jsonNode.fields();
//        while(iterator.hasNext()) {
//            //{"state":[{"anything-but":"initializing"}],"x-limit":[{"anything-but":123}]}
//            Map.Entry<String, JsonNode> entry = iterator.next();
//            //state
//            String key = entry.getKey();
//            //[{"anything-but":"initializing"}]  [{"anything-but":123}]}
//            JsonNode value = entry.getValue();
//
//                PatternEntry patternEntry = new PatternEntry(path+"."+key);
//                if(value.isArray()){
//                    for(JsonNode node : value){
//                        //{"anything-but":"initializing"}
//                        patternEntry.addRuleCondition(parseCondition(node));
//                    }
//                }
//                pattern.addDataList(patternEntry);
//            }

    }



    private static Condition parseCondition(JsonNode jsonNode){
        if(jsonNode.isValueNode()){
            return new ConditionsBuilder().withKey("specified").withParams(jsonNode).build();
        }

        Iterator<Map.Entry<String, JsonNode>> iterator = jsonNode.fields();
        while (iterator.hasNext()){
            Map.Entry<String, JsonNode> entry = iterator.next();
            //"anything-but"
            String key = entry.getKey();
            //"initializing"
            JsonNode value = entry.getValue();
            Condition condition =new ConditionsBuilder().withKey(key).withParams(value).build();
            return condition;
        }
       return null;
    }

    private static void parseRequiredField(String path, JsonNode jsonNode, Pattern pattern){
        //[
        //        {
        //            "prefix":"acs:oss:cn-hangzhou:1234567:xls-papk/"
        //        },
        //        {
        //            "suffix":".txt"
        //        },
        //        {
        //            "suffix":".jpg"
        //        }
        //    ]
        //传入的是【】数组，先判断数组是否为0
        if (jsonNode.isEmpty()) {
            // Empty array
            throw new JsonException("INVALID_PATTERN_VALUE ");
        }
        PatternEntry patternEntry = new PatternEntry(path);
       for(final JsonNode objNode: jsonNode ){
                //{
               //            "suffix":".jpg"
               //        }
         Condition condition =  parseCondition(objNode);
         patternEntry.addRuleCondition(condition);
       }

       pattern.addRequiredFieldList(patternEntry);

    }


    static class Node{
        private String path;
        private String key;
        private JsonNode value;

        Node(String path,String key, JsonNode value){
            this.path = path;
            this.key = key;
            this.value = value;
        }

        String getPath(){
            return this.path;
        }

        String getKey(){
            return this.key;
        }

        JsonNode getValue(){
            return this.value;
        }
    }

}
