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

package org.apache.eventmesh.runtime.exporter;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Because the class 'LoggingSpanExporter' in openTelemetry exported garbled code in eventMesh's startUp, I override the 'LoggingSpanExporter'.
 */
public class LogExporter implements SpanExporter {
    private static final Logger logger = LoggerFactory.getLogger(LogExporter.class);

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        // We always have 32 + 16 + name + several whitespace, 60 seems like an OK initial guess.
        StringBuilder sb = new StringBuilder(60);
        for (SpanData span : spans) {
            sb.setLength(0);
            InstrumentationLibraryInfo instrumentationLibraryInfo = span.getInstrumentationLibraryInfo();
            sb.append("'")
                    .append(span.getName())
                    .append("' : ")
                    .append(span.getTraceId())
                    .append(" ")
                    .append(span.getSpanId())
                    .append(" ")
                    .append(span.getKind())
                    .append(" [tracer: ")
                    .append(instrumentationLibraryInfo.getName())
                    .append(":")
                    .append(
                            instrumentationLibraryInfo.getVersion() == null
                                    ? ""
                                    : instrumentationLibraryInfo.getVersion())
                    .append("] ")
                    .append(span.getAttributes());
            logger.info(sb.toString());
        }
        return CompletableResultCode.ofSuccess();
    }

    /**
     * Flushes the data.
     * (i guess it is not necessary for slf4j's log)
     *
     * @return the result of the operation
     */
    @Override
    public CompletableResultCode flush() {
        CompletableResultCode resultCode = new CompletableResultCode();
        return resultCode.succeed();
    }

    @Override
    public CompletableResultCode shutdown() {
        return flush();
    }

}
