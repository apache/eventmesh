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

package org.apache.eventmesh.trace.pinpoint.exporter;


import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.trace.api.TracePluginFactory;
import org.apache.eventmesh.trace.pinpoint.PinpointTraceService;
import org.apache.eventmesh.trace.pinpoint.config.PinpointConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;

public class PinpointSpanExporterTest {

    private PinpointSpanExporter exporter;

    @Before
    public void setup() {
        PinpointTraceService pinpointTrace =
            (PinpointTraceService) TracePluginFactory.getEventMeshTraceService("pinpoint");

        PinpointConfiguration config = pinpointTrace.getClientConfiguration();

        this.exporter = new PinpointSpanExporter(
            config.getAgentId(),
            config.getAgentName(),
            config.getApplicationName(),
            config.getGrpcTransportConfig()
        );
    }

    @Test
    public void exportTest() {
        Collection<SpanData> spans = new ArrayList<>();
        Assert.assertEquals(CompletableResultCode.ofSuccess(), exporter.export(spans));

        spans.add(null);
        Assert.assertEquals(CompletableResultCode.ofSuccess(), exporter.export(spans));

        spans.clear();
        spans.add(new SpanDateTest());
        Assert.assertEquals(CompletableResultCode.ofSuccess(), exporter.export(spans));
    }

    @Test
    public void flushTest() {
        Assert.assertEquals(CompletableResultCode.ofSuccess(), exporter.flush());
    }

    @Test
    public void shutdownTest() {
        Assert.assertEquals(CompletableResultCode.ofSuccess(), exporter.shutdown());
    }

    /**
     * for test
     */
    private static class SpanDateTest implements SpanData {

        @Override
        public SpanContext getSpanContext() {
            return new SpanContextTest();
        }

        @Override
        public SpanContext getParentSpanContext() {
            return null;
        }

        @Override
        public Resource getResource() {
            return null;
        }

        @Override
        public InstrumentationLibraryInfo getInstrumentationLibraryInfo() {
            return null;
        }

        @Override
        public String getName() {
            return this.getClass().getName();
        }

        @Override
        public SpanKind getKind() {
            return SpanKind.INTERNAL;
        }

        @Override
        public long getStartEpochNanos() {
            return System.nanoTime();
        }

        @Override
        public Attributes getAttributes() {
            return null;
        }

        @Override
        public List<EventData> getEvents() {
            return null;
        }

        @Override
        public List<LinkData> getLinks() {
            return null;
        }

        @Override
        public StatusData getStatus() {
            return StatusData.ok();
        }

        @Override
        public long getEndEpochNanos() {
            return System.nanoTime();
        }

        @Override
        public boolean hasEnded() {
            return true;
        }

        @Override
        public int getTotalRecordedEvents() {
            return 0;
        }

        @Override
        public int getTotalRecordedLinks() {
            return 0;
        }

        @Override
        public int getTotalAttributeCount() {
            return 0;
        }
    }

    private static class SpanContextTest implements SpanContext {

        @Override
        public String getTraceId() {
            return UUID.randomUUID().toString();
        }

        @Override
        public String getSpanId() {
            return RandomStringUtils.generateNum(16);
        }

        @Override
        public TraceFlags getTraceFlags() {
            return null;
        }

        @Override
        public TraceState getTraceState() {
            return TraceState.getDefault();
        }

        @Override
        public boolean isRemote() {
            return false;
        }
    }
}
