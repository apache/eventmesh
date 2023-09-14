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

package org.apache.eventmesh.runtime.core.retry.strategies;

import javax.annotation.concurrent.Immutable;
import org.apache.eventmesh.common.enums.RetryStorageEnum;

/**
 * Factory class for {@link StorageStrategy} instances.
 */
public final class StorageStrategies {
    private StorageStrategies() {
    }

    /**
     * Returns a stop strategy which store in memory.
     */
    public static StorageStrategy storeInMemory() {
        return new StoreInMemory();
    }


    /**
     * Returns a stop strategy which store in memory.
     */
    public static StorageStrategy storeInStorage() {
        return new StoreInStorage();
    }

    @Immutable
    private static final class StoreInMemory implements StorageStrategy {

        @Override
        public RetryStorageEnum getStorageType() {
            return RetryStorageEnum.MEMORY;
        }
    }

    @Immutable
    private static final class StoreInStorage implements StorageStrategy {

        @Override
        public RetryStorageEnum getStorageType() {
            return RetryStorageEnum.STORAGE;
        }
    }
}
