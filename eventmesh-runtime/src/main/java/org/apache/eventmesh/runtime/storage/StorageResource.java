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

package org.apache.eventmesh.runtime.storage;

import org.apache.eventmesh.api.storage.StorageResourceService;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StorageResource {

    private static final Map<String, StorageResource> STORAGE_RESOURCE_CACHE = new HashMap<>(16);

    private StorageResourceService storageResourceService;

    private final AtomicBoolean inited = new AtomicBoolean(false);

    private final AtomicBoolean released = new AtomicBoolean(false);

    private StorageResource() {

    }

    public static StorageResource getInstance(String storageResourcePluginType) {
        return STORAGE_RESOURCE_CACHE.computeIfAbsent(storageResourcePluginType, StorageResource::storageResourceBuilder);
    }

    private static StorageResource storageResourceBuilder(String storageResourcePluginType) {
        StorageResourceService storageResourceServiceExt = EventMeshExtensionFactory.getExtension(StorageResourceService.class,
                storageResourcePluginType);
        if (storageResourceServiceExt == null) {
            String errorMsg = "can't load the StorageResourceService plugin, please check.";
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }
        StorageResource storageResource = new StorageResource();
        storageResource.storageResourceService = storageResourceServiceExt;
        return storageResource;
    }

    public void init() throws Exception {
        if (!inited.compareAndSet(false, true)) {
            return;
        }
        storageResourceService.init();
    }

    public void release() throws Exception {
        if (!released.compareAndSet(false, true)) {
            return;
        }
        inited.compareAndSet(true, false);
        storageResourceService.release();
    }
}
