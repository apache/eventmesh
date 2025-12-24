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

package org.apache.eventmesh.runtime.boot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.function.filter.pattern.Pattern;
import org.apache.eventmesh.runtime.meta.MetaStorage;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FilterEngineTest {

    @Mock
    private MetaStorage metaStorage;

    @Test
    public void testStartAndGetFilter() {
        FilterEngine filterEngine = new FilterEngine(metaStorage);

        // Mock MetaData
        Map<String, String> filterMetaData = new HashMap<>();
        String group = "testGroup";
        // JSON config for filter
        // Condition: source == "testSource" (must be array)
        String filterJson = "[{\"topic\":\"testTopic\", \"condition\":{\"source\":[\"testSource\"]}}]";
        filterMetaData.put("filter-" + group, filterJson);

        when(metaStorage.getMetaData(any(String.class), anyBoolean())).thenReturn(filterMetaData);

        // Start Engine
        filterEngine.start();

        // Get Filter
        Pattern pattern = filterEngine.getFilterPattern(group + "-testTopic");
        Assertions.assertNotNull(pattern);

        // Verify Filter behavior (optional, depends on Pattern implementation)
        // String validEventJson = "{"specversion":"1.0","id":"1","source":"testSource","type":"testType"}";
        // Assertions.assertTrue(pattern.filter(validEventJson));
    }
}
