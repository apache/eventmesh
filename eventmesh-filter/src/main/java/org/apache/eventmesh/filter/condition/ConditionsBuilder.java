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

package org.apache.eventmesh.filter.condition;

import com.fasterxml.jackson.databind.JsonNode;

public class ConditionsBuilder {

    private String key;
    private JsonNode jsonNode;

    public ConditionsBuilder withKey(String key) {
        this.key = key;
        return this;
    }

    public ConditionsBuilder withParams(JsonNode jsonNode) {
        this.jsonNode = jsonNode;
        return this;
    }

    public Condition build() {
        Condition condition = null;
        switch (this.key) {
            case "prefix":
                condition = new PrefixCondition(this.jsonNode);
                break;
            case "suffix":
                condition = new SuffixCondition(this.jsonNode);
                break;
            case "anything-but":
                condition = new AnythingButCondition(this.jsonNode);
                break;
            case "numeric":
                condition = new NumericCondition(this.jsonNode);
                break;
            case "exists":
                condition = new ExistsCondition(this.jsonNode);
                break;
            case "specified":
                condition = new SpecifiedCondition(this.jsonNode);
                break;
            default:
                throw new RuntimeException("INVALID CONDITION");
            // Add cases for other keys and conditions
        }

        return condition;
    }
}
