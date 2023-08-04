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

package org.apache.eventmesh.admin.api.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TopicCreateRequestTest {

    @Test
    public void testTopicCreateRequest() {
        String name = "testTopic";
        TopicCreateRequest topicCreateRequest = new TopicCreateRequest(name);

        assertEquals(name, topicCreateRequest.getTopic());
    }

    @Test
    public void testTopicCreateRequestSetName() {
        TopicCreateRequest topicCreateRequest = new TopicCreateRequest(null);
        assertNull(topicCreateRequest.getTopic());

        String name = "testTopic";
        topicCreateRequest.setTopic(name);
        assertEquals(name, topicCreateRequest.getTopic());
    }

    @Test
    public void testTopicCreateRequestSerialization() throws Exception {
        String topic = "testTopic";
        TopicCreateRequest topicCreateRequest = new TopicCreateRequest(topic);

        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(topicCreateRequest);

        assertTrue(json.contains("topic"));

        TopicCreateRequest deserializedRequest = objectMapper.readValue(json, TopicCreateRequest.class);

        assertEquals(topic, deserializedRequest.getTopic());
    }

}
