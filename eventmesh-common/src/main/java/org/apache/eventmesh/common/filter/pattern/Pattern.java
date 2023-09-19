package org.apache.eventmesh.common.filter.pattern;

import org.apache.eventmesh.common.filter.PatternEntry;
import org.apache.eventmesh.common.utils.JsonPathUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.PathNotFoundException;

public class Pattern {

    private List<PatternEntry> requiredFieldList =  new ArrayList<>();
    private List<PatternEntry> dataList = new ArrayList<>();



    private String content;

    public void addRequiredFieldList(PatternEntry patternEntry) {
        this.requiredFieldList.add(patternEntry);
    }

    public void addDataList(PatternEntry patternEntry) {
        this.dataList.add(patternEntry);
    }

    public boolean filter(String content) {
        this.content = content;
        //this.jsonNode = JacksonUtils.STRING_TO_JSONNODE(content);

        return matchRequiredFieldList(requiredFieldList)  && matchRequiredFieldList(dataList);
    }


    private boolean matchRequiredFieldList(List<PatternEntry> dataList) {

        for (final PatternEntry patternEntry : dataList) {
            JsonNode jsonElement = null;
            try {
                //content:filter
                String matchRes = JsonPathUtils.matchJsonPathValue(this.content, patternEntry.getPatternPath());

                if (StringUtils.isNoneBlank(matchRes)) {
                    jsonElement = JsonPathUtils.parseStrict(matchRes);
                }

                if (jsonElement != null && jsonElement.isArray()) {
                    for (JsonNode element : jsonElement) {
                        if (patternEntry.match(element)) {
                            return true;
                        }
                    }
                } else {
                    if (!patternEntry.match(jsonElement)) {
                        return false;
                    }
                }


            } catch (PathNotFoundException | JsonProcessingException e) {
                throw new RuntimeException(e);
            }


        }
        return true;

    }


}
