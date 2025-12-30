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

import org.apache.eventmesh.function.transformer.Transformer;
import org.apache.eventmesh.runtime.meta.MetaStorage;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TransformerEngineTest {

    @Mock
    private MetaStorage metaStorage;

    @Test
    public void testStartAndGetTransformer() throws Exception {
        TransformerEngine transformerEngine = new TransformerEngine(metaStorage);

        // Mock MetaData
        Map<String, String> transformerMetaData = new HashMap<>();
        String group = "testGroup";
        
        // JSON config for transformer
        // Use "original" which passes through
        String transformerJson = "[{\"topic\":\"testTopic\", \"transformerParam\":{\"transformerType\":\"original\"}}]";
        transformerMetaData.put("transformer-" + group, transformerJson);

        when(metaStorage.getMetaData(any(String.class), anyBoolean())).thenReturn(transformerMetaData);

        // Start Engine
        transformerEngine.start();

        // Get Transformer
        Transformer transformer = transformerEngine.getTransformer(group + "-testTopic");
        Assertions.assertNotNull(transformer);
        
        // Verify transform (original returns content as is)
        String content = "testContent";
        Assertions.assertEquals(content, transformer.transform(content));
    }
}
