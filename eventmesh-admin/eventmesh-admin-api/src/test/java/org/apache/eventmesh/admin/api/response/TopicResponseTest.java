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

package org.apache.eventmesh.admin.api.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TopicResponseTest {

    @Test
    public void testTopicResponse() {
        String topic = "testtopic";
        String createdTime = "2023-05-17 10:30:00";
        TopicResponse topicResponse = new TopicResponse(topic, createdTime);

        assertEquals(topic, topicResponse.getTopic());
        assertEquals(createdTime, topicResponse.getCreatedTime());
    }

    @Test
    public void testTopicResponseSerialization() throws Exception {
        String topic = "testtopic";
        String createdTime = "2023-05-17 10:30:00";
        TopicResponse topicResponse = new TopicResponse(topic, createdTime);

        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(topicResponse);

        assertTrue(json.contains("topic"));
        assertTrue(json.contains("created_time"));

        TopicResponse deserializedResponse = objectMapper.readValue(json, TopicResponse.class);

        assertEquals(topic, deserializedResponse.getTopic());
        assertEquals(createdTime, deserializedResponse.getCreatedTime());
    }
}
