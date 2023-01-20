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

package org.apache.eventmesh.webhook.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Manufacturer {

    private final Set<String> manufacturerSet = new HashSet<>();

    private final Map<String, List<String>> manufacturerEventMap = new ConcurrentHashMap<>();


    public Set<String> getManufacturerSet() {
        return manufacturerSet;
    }

    public void addManufacturer(final String manufacturer) {
        manufacturerSet.add(manufacturer);
    }

    public void removeManufacturer(final String manufacturer) {
        manufacturerSet.remove(manufacturer);
    }

    public Map<String, List<String>> getManufacturerEventMap() {
        return manufacturerEventMap;
    }

    public void setManufacturerEventMap(final Map<String, List<String>> manufacturerEventMap) {
        this.manufacturerEventMap.putAll(manufacturerEventMap);
    }

    public List<String> getManufacturerEvents(final String manufacturerName) {
        List<String> m = manufacturerEventMap.get(manufacturerName);
        if (m == null) {
            m = new ArrayList<>();
            manufacturerEventMap.put(manufacturerName, m);
        }

        return m;
    }
}
