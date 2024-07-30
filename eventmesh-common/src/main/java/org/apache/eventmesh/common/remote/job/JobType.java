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

package org.apache.eventmesh.common.remote.job;

import java.util.HashMap;
import java.util.Map;

public enum JobType {
    FULL,
    INCREASE,
    CHECK;

    private static final JobType[] STATES_NUM_INDEX = JobType.values();
    private static final Map<String, JobType> STATES_NAME_INDEX = new HashMap<>();
    static {
        for (JobType jobType : STATES_NUM_INDEX) {
            STATES_NAME_INDEX.put(jobType.name(), jobType);
        }
    }

    public static JobType fromIndex(Integer index) {
        if (index == null || index < 0 || index > STATES_NUM_INDEX.length) {
            return null;
        }

        return STATES_NUM_INDEX[index];
    }

    public static JobType fromIndex(String index) {
        if (index == null || index.isEmpty()) {
            return null;
        }

        return STATES_NAME_INDEX.get(index);
    }
}
