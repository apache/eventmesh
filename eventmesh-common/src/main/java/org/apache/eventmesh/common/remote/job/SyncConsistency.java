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

package org.apache.eventmesh.common.remote.job;

public enum SyncConsistency {
    /**
     * based with media
     */
    MEDIA("M"),
    /**
     * based with store
     */
    STORE("S"),
    /**
     * Based on the current change value, eventual consistency
     */
    BASE("B");

    private String value;

    SyncConsistency(String value) {
        this.value = value;
    }

    public static SyncConsistency valuesOf(String value) {
        SyncConsistency[] modes = values();
        for (SyncConsistency mode : modes) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return null;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isMedia() {
        return this.equals(SyncConsistency.MEDIA);
    }

    public boolean isStore() {
        return this.equals(SyncConsistency.STORE);
    }

    public boolean isBase() {
        return this.equals(SyncConsistency.BASE);
    }
}
