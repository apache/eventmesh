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

package org.apache.eventmesh.runtime.core.protocol.pipeline.filter;

import org.apache.eventmesh.common.protocol.pipeline.PipelineContext;
import org.apache.eventmesh.common.protocol.pipeline.PipelineResult;
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineFilter;

import java.net.URI;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Protocol compliance filter — validates that the CloudEvent conforms to
 * the CloudEvents 1.0 specification before entering deeper pipeline stages.
 *
 * <p>Checks required attributes: id, source, type, specversion.
 * Non-bypassable — malformed events should never reach downstream.
 */
@Slf4j
public class ProtocolFilter implements PipelineFilter {

    public static final String NAME = "ProtocolFilter";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int order() {
        return 3;
    }

    @Override
    public boolean isBypassable() {
        return false; // Data integrity — malformed events must be caught
    }

    @Override
    public PipelineResult filter(CloudEvent event, PipelineContext ctx) {
        // Required: id
        if (event.getId() == null || event.getId().isEmpty()) {
            log.warn("[ProtocolFilter] Event missing required 'id' attribute");
            return PipelineResult.drop(event);
        }

        // Required: specversion
        if (event.getSpecVersion() == null) {
            log.warn("[ProtocolFilter] Event {} missing required 'specversion'", event.getId());
            return PipelineResult.drop(event);
        }

        // Required: type
        if (event.getType() == null || event.getType().isEmpty()) {
            log.warn("[ProtocolFilter] Event {} missing required 'type'", event.getId());
            return PipelineResult.drop(event);
        }

        // Required: source
        if (event.getSource() == null) {
            log.warn("[ProtocolFilter] Event {} missing required 'source'", event.getId());
            return PipelineResult.drop(event);
        }

        // Validate source URI format
        try {
            URI sourceUri = event.getSource();
            if (sourceUri.getScheme() == null || sourceUri.getHost() == null) {
                log.warn("[ProtocolFilter] Event {} has invalid source URI: {}", event.getId(), sourceUri);
                return PipelineResult.drop(event);
            }
        } catch (Exception e) {
            log.warn("[ProtocolFilter] Event {} source is not a valid URI", event.getId());
            return PipelineResult.drop(event);
        }

        // Validate specversion format
        String sv = event.getSpecVersion().toString();
        if (!sv.equals("1.0")) {
            log.warn("[ProtocolFilter] Event {} has unsupported specversion: {}", event.getId(), sv);
            return PipelineResult.drop(event);
        }

        return PipelineResult.cont(event);
    }
}
