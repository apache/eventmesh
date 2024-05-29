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

package org.apache.eventmesh.common.remote.payload;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

public class PayloadFactory {
    private PayloadFactory(){
    }

    private static class PayloadFactoryHolder {
        private static final PayloadFactory INSTANCE = new PayloadFactory();
    }

    public static PayloadFactory getInstance(){
        return PayloadFactoryHolder.INSTANCE;
    }

    private final Map<String, Class<?>> registryPayload = new ConcurrentHashMap<>();

    private boolean initialized = false;

    public void init() {
        scan();
    }

    private synchronized void scan() {
        if (initialized) {
            return;
        }
        ServiceLoader<IPayload> payloads = ServiceLoader.load(IPayload.class);
        for (IPayload payload : payloads) {
            register(payload.getClass().getSimpleName(), payload.getClass());
        }
        initialized = true;
    }

    public void register(String type, Class<?> clazz) {
        if (Modifier.isAbstract(clazz.getModifiers())) {
            return;
        }
        if (registryPayload.containsKey(type)) {
            throw new RuntimeException(String.format("Fail to register, type:%s ,clazz:%s ", type, clazz.getName()));
        }
        registryPayload.put(type, clazz);
    }

    public Class<?> getClassByType(String type) {
        return registryPayload.get(type);
    }
}
