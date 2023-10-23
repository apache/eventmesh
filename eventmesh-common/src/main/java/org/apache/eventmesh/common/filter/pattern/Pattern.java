/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    private List<PatternEntry> requiredFieldList = new ArrayList<>();
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
        // this.jsonNode = JacksonUtils.STRING_TO_JSONNODE(content);

        return matchRequiredFieldList(requiredFieldList) && matchRequiredFieldList(dataList);
    }

    private boolean matchRequiredFieldList(List<PatternEntry> dataList) {

        for (final PatternEntry patternEntry : dataList) {
            JsonNode jsonElement = null;
            try {
                // content:filter
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
