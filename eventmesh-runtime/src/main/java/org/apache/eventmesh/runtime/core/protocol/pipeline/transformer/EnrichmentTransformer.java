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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * Enrichment transformer — attaches metadata, timestamps, and trace info.
 *
 * <p>Adds:
 * <ul>
 *   <li>{@code eventmesh_proxy_time} — ingress wall-clock time</li>
 *   <li>{@code eventmesh_trace_id} — from W3C traceparent or generated UUID</li>
 *   <li>{@code eventmesh_proxy_ip} — proxy node identity from context</li>
 *   <li>{@code eventmesh_protocol} — source protocol (TCP/HTTP/gRPC/A2A)</li>
 *   <li>{@code eventmesh_direction} — ingress/egress</li>
 * </ul>
 */
@Slf4j
public class EnrichmentTransformer implements PipelineTransformer {

    static final String EXT_PROXY_TIME = "eventmesh_proxy_time";
    static final String EXT_TRACE_ID = "eventmesh_trace_id";
    static final String EXT_PROXY_IP = "eventmesh_proxy_ip";
    static final String EXT_PROTOCOL = "eventmesh_protocol";
    static final String EXT_DIRECTION = "eventmesh_direction";

    @Override
    public String name() {
        return "enrichment";
    }

    @Override
    public int order() {
        return 300; // run after protocol normalization
    }

    @Override
    public CloudEvent transform(CloudEvent event, PipelineContext ctx) {
        CloudEventBuilder builder = CloudEventBuilder.from(event);

        // Attach proxy timestamp
        builder.withExtension(EXT_PROXY_TIME, String.valueOf(System.currentTimeMillis()));

        // Attach trace ID (use context traceId or generate)
        String traceId = ctx.getTraceId();
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString();
            ctx.setTraceId(traceId);
        }
        builder.withExtension(EXT_TRACE_ID, traceId);

        // Attach proxy IP from context attributes
        String proxyIp = (String) ctx.getAttribute("proxy.ip");
        if (proxyIp != null) {
            builder.withExtension(EXT_PROXY_IP, proxyIp);
        }

        // Attach protocol and direction
        builder.withExtension(EXT_PROTOCOL, ctx.getEntryProtocol());
        builder.withExtension(EXT_DIRECTION, ctx.getDirection().name());

        if (log.isTraceEnabled()) {
            log.trace("EnrichmentTransformer: enriched event {} with traceId={} protocol={}",
                event.getId(), traceId, ctx.getEntryProtocol());
        }

        return builder.build();
    }
}
