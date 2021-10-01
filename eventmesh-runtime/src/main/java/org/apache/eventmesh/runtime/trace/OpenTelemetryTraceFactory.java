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

package org.apache.eventmesh.runtime.trace;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.runtime.exporter.EventMeshExporter;
import org.apache.eventmesh.runtime.exporter.LogExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

public class OpenTelemetryTraceFactory {
    private static final Logger logger = LoggerFactory.getLogger(OpenTelemetryTraceFactory.class);

    private OpenTelemetry openTelemetry;

    private SpanExporter spanExporter;

    private SpanExporter defaultExporter = new LogExporter();

    private SpanProcessor spanProcessor;

    // Name of the service(using the instrumentationName)
    private final String SERVICE_NAME = "eventmesh_trace";

    public OpenTelemetryTraceFactory(CommonConfiguration configuration){
        try {
            //different spanExporter
            String exporterName = configuration.eventMeshTraceExporterType;
            //use reflection to get spanExporter
            String className = String.format("org.apache.eventmesh.runtime.exporter.%sExporter",exporterName);
            EventMeshExporter eventMeshExporter = (EventMeshExporter) Class.forName(className).newInstance();
            spanExporter = eventMeshExporter.getSpanExporter(configuration);
        }catch (Exception ex){
            logger.error("fail to set tracer's exporter,due to {}",ex.getMessage());
            //fail to set the exporter in configuration, changing to use the default Exporter
            spanExporter = defaultExporter;
            logger.info("change to use the default exporter {}",defaultExporter.getClass());
        }

        // Configure the batch spans processor. This span processor exports span in batches.
        spanProcessor = BatchSpanProcessor.builder(spanExporter)
                .setMaxExportBatchSize(configuration.eventMeshTraceMaxExportSize)// set the maximum batch size to use
                .setMaxQueueSize(configuration.eventMeshTraceMaxQueueSize) // set the queue size. This must be >= the export batch size
                .setExporterTimeout(configuration.eventMeshTraceExporterTimeout, TimeUnit.SECONDS) // set the max amount of time an export can run before getting
                .setScheduleDelay(configuration.eventMeshTraceExportInterval, TimeUnit.SECONDS)// set time between two different exports
                .build();

        //set the trace service's name
        Resource serviceNameResource =
                Resource.create(Attributes.of(stringKey("service.name"), SERVICE_NAME));

        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                        .addSpanProcessor(spanProcessor)
                        .setResource(Resource.getDefault().merge(serviceNameResource))
                        .build();

        openTelemetry = OpenTelemetrySdk.builder()
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .setTracerProvider(sdkTracerProvider)
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(sdkTracerProvider::close));
    }

    //Gets or creates a named tracer instance
    public Tracer getTracer(String instrumentationName){
        return openTelemetry.getTracer(instrumentationName);
    }

    //to inject or extract span context
    public TextMapPropagator getTextMapPropagator() {return openTelemetry.getPropagators().getTextMapPropagator();}
}
