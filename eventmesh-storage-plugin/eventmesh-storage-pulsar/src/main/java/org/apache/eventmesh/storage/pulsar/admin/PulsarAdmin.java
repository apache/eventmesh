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

package org.apache.eventmesh.storage.pulsar.admin;

import org.apache.eventmesh.api.admin.AbstractAdmin;
import org.apache.eventmesh.api.admin.TopicProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;

public class PulsarAdmin extends AbstractAdmin {

    public PulsarAdmin() {
        super(new AtomicBoolean(false));
    }

    @Override
    public List<TopicProperties> getTopic() {
        // TODO implement admin functions
        return new ArrayList<>();
    }

    @Override
    public void createTopic(String topicName) {
    }

    @Override
    public void deleteTopic(String topicName) {
    }

    @Override
    public void publish(CloudEvent cloudEvent) throws Exception {
    }

    @Override
    public void shutdown() {
    }
}
