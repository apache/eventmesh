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

package org.apache.eventmesh.api.connector.storage.pull;

import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.api.connector.storage.StorageConnector;

import java.util.ArrayList;
import java.util.List;

import io.cloudevents.CloudEvent;

import lombok.Setter;

public class StorageAsyncConsumeContext extends EventMeshAsyncConsumeContext {

    @Setter
    private StorageConnector storageConnector;

    @Setter
    private CloudEvent cloudEvent;

    @Override
    public void commit(EventMeshAction action) {
        switch (action) {
            case CommitMessage:
                List<CloudEvent> cloudEventList = new ArrayList<>(1);
                cloudEventList.add(cloudEvent);
                storageConnector.updateOffset(cloudEventList, this.getAbstractContext());
                break;
            case ReconsumeLater:
                break;
            case ManualAck:
                break;
            default:
                break;
        }
    }

}
