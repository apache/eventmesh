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

package org.apache.eventmesh.filter.api;

import org.apache.eventmesh.filter.api.loader.FilterLoader;
import org.apache.eventmesh.filter.api.loader.StaticFilterLoader;

import java.util.SortedSet;

import io.cloudevents.CloudEvent;

public class FilterChainInitializer {

    private final FilterLoader filterLoader = new StaticFilterLoader();

    //todo: load filters
    public EventMeshFilterChainHandler init() {

        // ordered with filter order
        final EventMeshFilter<CloudEvent, CloudEvent>[] sourceFilters = getFilters(
            FilterType.SOURCE);

        // source filter chain
        final FilterRunner<CloudEvent> sourceFilterChain = new FilterChainRunner<>(sourceFilters);

        // ordered with filter order
        final EventMeshFilter<CloudEvent, CloudEvent>[] sinkFilters = getFilters(
            FilterType.SINK);

        // source filter chain
        final FilterRunner<CloudEvent> sinkFilterChain = new FilterChainRunner<>(sinkFilters);

        return new EventMeshFilterChainHandler(sourceFilterChain, sinkFilterChain);

    }

    private <T extends CloudEvent> EventMeshFilter<T, T>[] getFilters(FilterType filterType) {
        final SortedSet<EventMeshFilter<?, ?>> meshFilters = filterLoader.getFiltersByType(filterType);
        final EventMeshFilter<T, T>[] filters = new EventMeshFilter[meshFilters.size()];
        int i = 0;
        for (EventMeshFilter<?, ?> eventMeshFilter : meshFilters) {
            filters[i] = (EventMeshFilter<T, T>) eventMeshFilter;
            i++;
        }

        return filters;
    }
}
