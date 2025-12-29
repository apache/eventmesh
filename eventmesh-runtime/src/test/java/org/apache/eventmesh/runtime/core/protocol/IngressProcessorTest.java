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

package org.apache.eventmesh.runtime.core.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.function.api.Router;
import org.apache.eventmesh.function.filter.pattern.Pattern;
import org.apache.eventmesh.function.transformer.Transformer;
import org.apache.eventmesh.runtime.boot.FilterEngine;
import org.apache.eventmesh.runtime.boot.RouterEngine;
import org.apache.eventmesh.runtime.boot.TransformerEngine;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class IngressProcessorTest {

    @Mock
    private FilterEngine filterEngine;

    @Mock
    private TransformerEngine transformerEngine;

    @Mock
    private RouterEngine routerEngine;

    private IngressProcessor ingressProcessor;

    private static final String PIPELINE_KEY = "testGroup-testTopic";

    @BeforeEach
    public void setUp() {
        ingressProcessor = new IngressProcessor(filterEngine, transformerEngine, routerEngine);
    }

    private CloudEvent createTestEvent(String data) {
        return CloudEventBuilder.v1()
            .withId("test-id-1")
            .withSource(URI.create("test://source"))
            .withType("test.type")
            .withSubject("testTopic")
            .withData(data.getBytes(StandardCharsets.UTF_8))
            .build();
    }

    @Test
    public void testProcess_NoPipeline_EventPassThrough() {
        // Given: No filter, transformer, or router configured
        when(filterEngine.getFilterPattern(PIPELINE_KEY)).thenReturn(null);
        when(transformerEngine.getTransformer(PIPELINE_KEY)).thenReturn(null);
        when(routerEngine.getRouter(PIPELINE_KEY)).thenReturn(null);

        CloudEvent event = createTestEvent("test data");

        // When
        CloudEvent result = ingressProcessor.process(event, PIPELINE_KEY);

        // Then: Event should pass through unchanged
        assertNotNull(result);
        assertEquals("testTopic", result.getSubject());
        assertEquals("test data", new String(result.getData().toBytes(), StandardCharsets.UTF_8));

        verify(filterEngine).getFilterPattern(PIPELINE_KEY);
        verify(transformerEngine).getTransformer(PIPELINE_KEY);
        verify(routerEngine).getRouter(PIPELINE_KEY);
    }

    @Test
    public void testProcess_FilterPass_EventPassThrough() {
        // Given: Filter configured and passes
        Pattern filterPattern = mock(Pattern.class);
        when(filterPattern.filter("test data")).thenReturn(true);
        when(filterEngine.getFilterPattern(PIPELINE_KEY)).thenReturn(filterPattern);
        when(transformerEngine.getTransformer(PIPELINE_KEY)).thenReturn(null);
        when(routerEngine.getRouter(PIPELINE_KEY)).thenReturn(null);

        CloudEvent event = createTestEvent("test data");

        // When
        CloudEvent result = ingressProcessor.process(event, PIPELINE_KEY);

        // Then: Event should pass
        assertNotNull(result);
        verify(filterPattern).filter("test data");
    }

    @Test
    public void testProcess_FilterReject_ReturnNull() {
        // Given: Filter configured and rejects
        Pattern filterPattern = mock(Pattern.class);
        when(filterPattern.filter("test data")).thenReturn(false);
        when(filterEngine.getFilterPattern(PIPELINE_KEY)).thenReturn(filterPattern);

        CloudEvent event = createTestEvent("test data");

        // When
        CloudEvent result = ingressProcessor.process(event, PIPELINE_KEY);

        // Then: Event should be filtered out (return null)
        assertNull(result);
        verify(filterPattern).filter("test data");
    }

    @Test
    public void testProcess_TransformerModifiesData() throws Exception {
        // Given: Transformer configured
        Transformer transformer = mock(Transformer.class);
        when(transformer.transform("original data")).thenReturn("transformed data");

        when(filterEngine.getFilterPattern(PIPELINE_KEY)).thenReturn(null);
        when(transformerEngine.getTransformer(PIPELINE_KEY)).thenReturn(transformer);
        when(routerEngine.getRouter(PIPELINE_KEY)).thenReturn(null);

        CloudEvent event = createTestEvent("original data");

        // When
        CloudEvent result = ingressProcessor.process(event, PIPELINE_KEY);

        // Then: Event data should be transformed
        assertNotNull(result);
        assertEquals("transformed data", new String(result.getData().toBytes(), StandardCharsets.UTF_8));
        assertEquals("testTopic", result.getSubject()); // Subject unchanged
        verify(transformer).transform("original data");
    }

    @Test
    public void testProcess_RouterModifiesTopic() {
        // Given: Router configured
        Router router = mock(Router.class);
        when(router.route("test data")).thenReturn("newTopic");

        when(filterEngine.getFilterPattern(PIPELINE_KEY)).thenReturn(null);
        when(transformerEngine.getTransformer(PIPELINE_KEY)).thenReturn(null);
        when(routerEngine.getRouter(PIPELINE_KEY)).thenReturn(router);

        CloudEvent event = createTestEvent("test data");

        // When
        CloudEvent result = ingressProcessor.process(event, PIPELINE_KEY);

        // Then: Event subject (topic) should be routed to new topic
        assertNotNull(result);
        assertEquals("newTopic", result.getSubject());
        assertEquals("test data", new String(result.getData().toBytes(), StandardCharsets.UTF_8)); // Data unchanged
        verify(router).route("test data");
    }

    @Test
    public void testProcess_FullPipeline_FilterTransformRoute() throws Exception {
        // Given: All three components configured
        Pattern filterPattern = mock(Pattern.class);
        when(filterPattern.filter("original data")).thenReturn(true);

        Transformer transformer = mock(Transformer.class);
        when(transformer.transform("original data")).thenReturn("transformed data");

        Router router = mock(Router.class);
        when(router.route("transformed data")).thenReturn("routedTopic");

        when(filterEngine.getFilterPattern(PIPELINE_KEY)).thenReturn(filterPattern);
        when(transformerEngine.getTransformer(PIPELINE_KEY)).thenReturn(transformer);
        when(routerEngine.getRouter(PIPELINE_KEY)).thenReturn(router);

        CloudEvent event = createTestEvent("original data");

        // When
        CloudEvent result = ingressProcessor.process(event, PIPELINE_KEY);

        // Then: Event should go through all stages
        assertNotNull(result);
        assertEquals("transformed data", new String(result.getData().toBytes(), StandardCharsets.UTF_8));
        assertEquals("routedTopic", result.getSubject());

        verify(filterPattern).filter("original data");
        verify(transformer).transform("original data");
        verify(router).route("transformed data");
    }

    @Test
    public void testProcess_FilterException_ThrowsRuntimeException() {
        // Given: Filter throws exception
        Pattern filterPattern = mock(Pattern.class);
        when(filterPattern.filter(anyString())).thenThrow(new RuntimeException("Filter error"));
        when(filterEngine.getFilterPattern(PIPELINE_KEY)).thenReturn(filterPattern);

        CloudEvent event = createTestEvent("test data");

        // When & Then: Should throw RuntimeException
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            ingressProcessor.process(event, PIPELINE_KEY);
        });

        assertEquals("Ingress pipeline exception", exception.getMessage());
    }

    @Test
    public void testProcess_TransformerException_ThrowsRuntimeException() throws Exception {
        // Given: Transformer throws exception
        Transformer transformer = mock(Transformer.class);
        when(transformer.transform("test data")).thenThrow(new RuntimeException("Transformer error"));

        when(filterEngine.getFilterPattern(PIPELINE_KEY)).thenReturn(null);
        when(transformerEngine.getTransformer(PIPELINE_KEY)).thenReturn(transformer);
        when(routerEngine.getRouter(PIPELINE_KEY)).thenReturn(null);

        CloudEvent event = createTestEvent("test data");

        // When & Then: Should throw RuntimeException
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            ingressProcessor.process(event, PIPELINE_KEY);
        });

        assertEquals("Ingress pipeline exception", exception.getMessage());
    }

    @Test
    public void testProcess_RouterException_ThrowsRuntimeException() {
        // Given: Router throws exception
        Router router = mock(Router.class);
        when(router.route(anyString())).thenThrow(new RuntimeException("Router error"));

        when(filterEngine.getFilterPattern(PIPELINE_KEY)).thenReturn(null);
        when(transformerEngine.getTransformer(PIPELINE_KEY)).thenReturn(null);
        when(routerEngine.getRouter(PIPELINE_KEY)).thenReturn(router);

        CloudEvent event = createTestEvent("test data");

        // When & Then: Should throw RuntimeException
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            ingressProcessor.process(event, PIPELINE_KEY);
        });

        assertEquals("Ingress pipeline exception", exception.getMessage());
    }

    @Test
    public void testProcess_EventWithoutData_NoPipelineApplied() {
        // Given: Event with null data
        when(filterEngine.getFilterPattern(PIPELINE_KEY)).thenReturn(mock(Pattern.class));
        when(transformerEngine.getTransformer(PIPELINE_KEY)).thenReturn(mock(Transformer.class));
        when(routerEngine.getRouter(PIPELINE_KEY)).thenReturn(mock(Router.class));

        CloudEvent event = CloudEventBuilder.v1()
            .withId("test-id-2")
            .withSource(URI.create("test://source"))
            .withType("test.type")
            .withSubject("testTopic")
            .build(); // No data

        // When
        CloudEvent result = ingressProcessor.process(event, PIPELINE_KEY);

        // Then: Event should pass through (pipeline skipped for null data)
        assertNotNull(result);
        assertNull(result.getData());
    }

    @Test
    public void testProcess_DifferentPipelineKeys() {
        // Given: Different pipeline keys
        Pattern filterPattern1 = mock(Pattern.class);
        Pattern filterPattern2 = mock(Pattern.class);
        when(filterPattern1.filter(anyString())).thenReturn(true);
        when(filterPattern2.filter(anyString())).thenReturn(false);

        when(filterEngine.getFilterPattern("group1-topic1")).thenReturn(filterPattern1);
        when(filterEngine.getFilterPattern("group2-topic2")).thenReturn(filterPattern2);
        when(transformerEngine.getTransformer(anyString())).thenReturn(null);
        when(routerEngine.getRouter(anyString())).thenReturn(null);

        CloudEvent event = createTestEvent("test data");

        // When
        CloudEvent result1 = ingressProcessor.process(event, "group1-topic1");
        CloudEvent result2 = ingressProcessor.process(event, "group2-topic2");

        // Then: Different results based on pipeline key
        assertNotNull(result1); // Passed filter
        assertNull(result2);    // Filtered out

        verify(filterEngine).getFilterPattern("group1-topic1");
        verify(filterEngine).getFilterPattern("group2-topic2");
    }
}
