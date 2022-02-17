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

package org.apache.eventmesh.runtime.core.protocol.grpc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.stub.StreamObserver;

public class EventEmitter<T> {
    private final Logger logger = LoggerFactory.getLogger(EventEmitter.class);

    private final StreamObserver<T> emitter;

    public EventEmitter(StreamObserver<T> emitter) {
        this.emitter = emitter;
    }

    public synchronized void onNext(T event) {
        try {
            emitter.onNext(event);
        } catch (Throwable t) {
            logger.warn("StreamObserver Error onNext. {}", t.getMessage());
        }
    }

    public synchronized void onCompleted() {
        try {
            emitter.onCompleted();
        } catch (Throwable t) {
            logger.warn("StreamObserver Error onCompleted. {}", t.getMessage());
        }
    }

    public synchronized void onError(Throwable t) {
        try {
            emitter.onError(t);
        } catch (Throwable t1) {
            logger.warn("StreamObserver Error onError. {}", t1.getMessage());
        }
    }

    public StreamObserver<T> getEmitter() {
        return emitter;
    }
}
