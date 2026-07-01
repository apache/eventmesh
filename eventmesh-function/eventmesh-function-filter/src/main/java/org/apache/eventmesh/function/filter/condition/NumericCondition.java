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

package org.apache.eventmesh.function.filter.condition;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public class NumericCondition implements Condition {

    List<String> operators = new ArrayList<>();
    List<Double> nums = new ArrayList<>();

    NumericCondition(JsonNode numeric) {
        if (numeric.isArray()) {
            if (numeric.size() % 2 != 0) {
                throw new RuntimeException("NUMERIC NO RIGHT FORMAT");
            }
            for (int i = 0; i < numeric.size(); i += 2) {
                JsonNode opt = numeric.get(i);
                JsonNode number = numeric.get(i + 1);
                operators.add(opt.asText());
                nums.add(number.asDouble());
            }
        } else {
            throw new RuntimeException("NUMERIC MUST BE ARRAY");
        }
    }

    private boolean compareNums(Double rule, Double target, String opt) {
        int res = Double.compare(target, rule);
        switch (opt) {
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
        if (inputEvent.isNumber()) {
            for (int i = 0; i < operators.size(); ++i) {
                if (!compareNums(nums.get(i), inputEvent.asDouble(), operators.get(i))) {
                    return false;
                }

            }

        }

        return true;
    }
}
