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

package org.apache.eventmesh.filter.api.loader;

import org.apache.eventmesh.filter.api.EventMeshFilter;
import org.apache.eventmesh.filter.api.FilterType;
import org.apache.eventmesh.filter.api.factory.FilterFactory;
import org.apache.eventmesh.filter.api.factory.StaticFilterPluginFactory;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import io.cloudevents.CloudEvent;

public class StaticFilterLoader implements FilterLoader {

    private static final FilterFactory filterFactory = new StaticFilterPluginFactory();

    private static final List<String> FILTER_NAME_LIST = Arrays.asList("source-default");

    private static final Map<FilterType, SortedSet<EventMeshFilter<?, ?>>> FILTER_TYPE_SORTED_SET_MAP
        = new EnumMap<>(FilterType.class);

    static {
        loadFilters();
    }

    //todo: load filters when server init

    @Override
    public SortedSet<EventMeshFilter<?, ?>> getFiltersByType(FilterType filterType) {

        return FILTER_TYPE_SORTED_SET_MAP.get(filterType);
    }

    public static void loadFilters() {
        for (String filterName : FILTER_NAME_LIST) {
            EventMeshFilter<CloudEvent, CloudEvent> eventMeshFilter = filterFactory.getMeshFilter(filterName);

            FILTER_TYPE_SORTED_SET_MAP.computeIfAbsent(eventMeshFilter.filterType(),
                k -> new TreeSet<>(FILTER_COMPARATOR)).add(eventMeshFilter);
        }
    }
}
