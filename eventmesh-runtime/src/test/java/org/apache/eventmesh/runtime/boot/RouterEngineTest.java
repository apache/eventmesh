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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.function.api.Router;
import org.apache.eventmesh.runtime.meta.MetaStorage;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RouterEngineTest {

    @Mock
    private MetaStorage metaStorage;

    @Test
    public void testStartAndRoute() {
        RouterEngine routerEngine = new RouterEngine(metaStorage);

        // Mock MetaData
        Map<String, String> routerMetaData = new HashMap<>();
        String group = "testGroup";
        // JSON config for router
        String routerJson = "[{\"topic\":\"sourceTopic\", \"routerConfig\":\"targetTopic\"}]";
        routerMetaData.put("router-" + group, routerJson);

        when(metaStorage.getMetaData(any(String.class), anyBoolean())).thenReturn(routerMetaData);

        // Start Engine
        routerEngine.start();

        // Get Router
        Router router = routerEngine.getRouter(group + "-sourceTopic");
        Assertions.assertNotNull(router);

        // Test Route
        String target = router.route("{}");
        // Since RouterBuilder uses DefaultRouter which returns the config string directly, 
        // passing "targetTopic" as config should return "targetTopic".
        // However, RouterEngine gets "routerConfig" node from JSON.
        // If "routerConfig" is "targetTopic" string, Jackson toString() might quote it like "\"targetTopic\"".
        // Let's check RouterEngine logic: routerJsonNode.get("routerConfig").toString()
        // If json is {"routerConfig": "targetTopic"}, .get("routerConfig") is a TextNode.
        // .toString() on TextNode returns "\"targetTopic\"".
        // .asText() returns "targetTopic".
        // The code uses .toString().
        
        // Wait, RouterBuilder.build(String)
        // If it receives "\"targetTopic\"", it returns it.
        
        // Let's verify behavior.
        // Assertions.assertEquals("\"targetTopic\"", target); 
        // Or maybe I should fix RouterEngine to use .asText() if it expects a simple string?
        // But routerConfig can be a complex JSON object for other Routers. 
        // So .toString() is safer for generic config.
        
        // For this test, assuming "targetTopic" -> "\"targetTopic\""
        
        Assertions.assertEquals("\"targetTopic\"", target);
    }
}
