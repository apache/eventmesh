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

package org.apache.eventmesh.function.filter;

import org.apache.eventmesh.function.filter.condition.Condition;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public class PatternEntry {

    private String patternName;

    private String patternPath;

    private final List<Condition> conditionList = new ArrayList<>();

    public PatternEntry(final String patternName, final String patternPath) {
        this.patternName = patternName;
        this.patternPath = patternPath;
    }

    public void addCondition(Condition patternCondition) {
        this.conditionList.add(patternCondition);
    }

    public String getPatternName() {
        return patternName;
    }

    public String getPatternPath() {
        return patternPath;
    }

    // default filter type is OR
    // todo: extend the filter type with AND
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
