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


import java.util.*;

/**
 * 厂商信息汇总实体
 */
public class ManufacturerObject {

    private Set<String> manufacturerList = new HashSet<>();

    private Map<String , List<String>> manufacturerEventMap = new HashMap<>();


    public Set<String> getManufacturerList() {
        return manufacturerList;
    }

    public Set<String> addManufacturer(String manufacturer) {
        manufacturerList.add(manufacturer);
        return manufacturerList;
    }

    public Set<String> removeManufacturer(String manufacturer) {
        manufacturerList.remove(manufacturer);
        return manufacturerList;
    }

    public Map<String, List<String>> getManufacturerEventMap() {
        return manufacturerEventMap;
    }

    public void setManufacturerEventMap(Map<String, List<String>> manufacturerEventMap) {
        this.manufacturerEventMap = manufacturerEventMap;
    }

    public List<String> getManufacturerEvents(String manufacturerName) {
        if (!manufacturerEventMap.containsKey(manufacturerName)) {
            List<String> m = new ArrayList<>();
            manufacturerEventMap.put(manufacturerName, m);
            return m;
        }
        return manufacturerEventMap.get(manufacturerName);
    }
}
