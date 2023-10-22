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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Manufacturer {

    private Set<String> manufacturerSet = new HashSet<>();

    private Map<String, List<String>> manufacturerEventMap = new ConcurrentHashMap<>();

    public Set<String> getManufacturerSet() {
        return manufacturerSet;
    }

    public Set<String> addManufacturer(String manufacturer) {
        manufacturerSet.add(manufacturer);
        return manufacturerSet;
    }

    public Set<String> removeManufacturer(String manufacturer) {
        manufacturerSet.remove(manufacturer);
        return manufacturerSet;
    }

    public Map<String, List<String>> getManufacturerEventMap() {
        return manufacturerEventMap;
    }

    public void setManufacturerEventMap(Map<String, List<String>> manufacturerEventMap) {
        this.manufacturerEventMap = manufacturerEventMap;
    }

    public List<String> getManufacturerEvents(String manufacturerName) {
        manufacturerEventMap.putIfAbsent(manufacturerName, new ArrayList<String>());
        return manufacturerEventMap.get(manufacturerName);
    }
}
