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

package org.apache.eventmesh.connector.pravega.client;

import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;

import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;
import io.pravega.client.stream.EventRead;
import io.pravega.client.stream.EventStreamReader;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SubscribeTask extends Thread {
    private final EventStreamReader<byte[]> reader;
    private final EventListener listener;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean continueRead = new AtomicBoolean(true);

    public SubscribeTask(String name, EventStreamReader<byte[]> reader, EventListener listener) {
        super(name);
        this.reader = reader;
        this.listener = listener;
    }

    @Override
    public void run() {
        CloudEvent cloudEvent = null;
        while (running.get()) {
            if (continueRead.get()) {
                EventRead<byte[]> event = reader.readNextEvent(2000);
                if (event == null) {
                    continue;
                }
                byte[] eventByteArray = event.getEvent();
                if (eventByteArray == null) {
                    continue;
                }
                PravegaEvent pravegaEvent = PravegaEvent.getFromByteArray(eventByteArray);
                cloudEvent = pravegaEvent.convertToCloudEvent();

                listener.consume(cloudEvent, new PravegaEventMeshAsyncConsumeContext());
            } else {
                listener.consume(cloudEvent, new PravegaEventMeshAsyncConsumeContext());
            }
        }
    }

    public void stopRead() {
        running.compareAndSet(true, false);
    }

    private class PravegaEventMeshAsyncConsumeContext extends EventMeshAsyncConsumeContext {
        @Override
        public void commit(EventMeshAction action) {
            switch (action) {
                case CommitMessage:
                case ReconsumeLater:
                    continueRead.set(false);
                    break;
                case ManualAck:
                    continueRead.set(true);
                    break;
                default:
            }
        }
    }
}
