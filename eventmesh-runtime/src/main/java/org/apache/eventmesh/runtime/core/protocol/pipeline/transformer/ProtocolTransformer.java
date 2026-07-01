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

package org.apache.eventmesh.runtime.core.protocol.pipeline.transformer;

import org.apache.eventmesh.common.protocol.pipeline.PipelineContext;
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineTransformer;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * Protocol transformer — normalizes CloudEvents across protocol boundaries.
 *
 * <p>Ensures every event has required CloudEvents attributes after crossing
 * TCP/HTTP/gRPC boundaries where protocol-specific adaptors may omit fields.
 */
@Slf4j
public class ProtocolTransformer implements PipelineTransformer {

    static final String ATTR_EVENTMESH_STORE_TIMESTAMP = "eventmesh_store_timestamp";

    @Override
    public String name() {
        return "protocol";
    }

    @Override
    public int order() {
        return 100; // run first among transformers
    }

    @Override
    public CloudEvent transform(CloudEvent event, PipelineContext ctx) {
        // Ensure mandatory CloudEvents fields are present
        CloudEventBuilder builder = CloudEventBuilder.from(event);

        if (event.getId() == null || event.getId().isEmpty()) {
            String generatedId = java.util.UUID.randomUUID().toString();
            builder.withId(generatedId);
            log.debug("ProtocolTransformer: generated missing id {}", generatedId);
        }

        if (event.getSource() == null) {
            builder.withSource(java.net.URI.create("/eventmesh/default"));
        }

        // Note: specversion is set by the CloudEvents library internally
        // based on the CloudEventBuilder defaults; we skip explicit set.

        if (event.getType() == null || event.getType().isEmpty()) {
            builder.withType("org.eventmesh.unknown");
        }

        // Normalize content-type to application/json default
        String contentType = event.getDataContentType();
        if (contentType == null || contentType.isEmpty()) {
            builder.withDataContentType("application/json");
        }

        // Attach store timestamp for ordered consumers
        Map<String, Object> exts = (Map<String, Object>) (Map<?, ?>) event.getExtensionNames()
                .stream()
                .collect(java.util.stream.Collectors.toMap(k -> k, event::getExtension));
        exts.put(ATTR_EVENTMESH_STORE_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
        for (Map.Entry<String, Object> ext : exts.entrySet()) {
            if (ext.getValue() instanceof String) {
                builder.withExtension(ext.getKey(), (String) ext.getValue());
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("ProtocolTransformer: normalized event id={} source={} type={}",
                builder.build().getId(), builder.build().getSource(), builder.build().getType());
        }
        return builder.build();
    }
}
