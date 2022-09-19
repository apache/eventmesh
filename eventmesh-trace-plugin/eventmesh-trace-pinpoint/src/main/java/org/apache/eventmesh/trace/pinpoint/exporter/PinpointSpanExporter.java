/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.apache.eventmesh.trace.pinpoint.exporter;

import static com.navercorp.pinpoint.common.trace.AnnotationKey.API;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import org.apache.eventmesh.trace.pinpoint.common.PinpointConstants;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.grpc.NameResolverProvider;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.internal.OtelEncodingUtils;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.internal.ThrottlingLogger;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import com.navercorp.pinpoint.common.trace.ServiceType;
import com.navercorp.pinpoint.common.util.JvmUtils;
import com.navercorp.pinpoint.common.util.SystemPropertyKey;
import com.navercorp.pinpoint.grpc.AgentHeaderFactory;
import com.navercorp.pinpoint.grpc.client.ChannelFactory;
import com.navercorp.pinpoint.grpc.client.ChannelFactoryBuilder;
import com.navercorp.pinpoint.grpc.client.DefaultChannelFactoryBuilder;
import com.navercorp.pinpoint.grpc.client.HeaderFactory;
import com.navercorp.pinpoint.profiler.AgentInfoSender;
import com.navercorp.pinpoint.profiler.JvmInformation;
import com.navercorp.pinpoint.profiler.context.Annotation;
import com.navercorp.pinpoint.profiler.context.DefaultServerMetaDataRegistryService;
import com.navercorp.pinpoint.profiler.context.ServerMetaDataRegistryService;
import com.navercorp.pinpoint.profiler.context.Span;
import com.navercorp.pinpoint.profiler.context.SpanEvent;
import com.navercorp.pinpoint.profiler.context.annotation.Annotations;
import com.navercorp.pinpoint.profiler.context.compress.GrpcSpanProcessorV2;
import com.navercorp.pinpoint.profiler.context.grpc.GrpcAgentInfoMessageConverter;
import com.navercorp.pinpoint.profiler.context.grpc.GrpcSpanMessageConverter;
import com.navercorp.pinpoint.profiler.context.grpc.config.GrpcTransportConfig;
import com.navercorp.pinpoint.profiler.context.id.DefaultTraceIdFactory;
import com.navercorp.pinpoint.profiler.context.id.DefaultTraceRootFactory;
import com.navercorp.pinpoint.profiler.context.id.TraceIdFactory;
import com.navercorp.pinpoint.profiler.context.id.TraceRoot;
import com.navercorp.pinpoint.profiler.context.id.TraceRootFactory;
import com.navercorp.pinpoint.profiler.context.provider.AgentInformationProvider;
import com.navercorp.pinpoint.profiler.context.provider.grpc.DnsExecutorServiceProvider;
import com.navercorp.pinpoint.profiler.context.provider.grpc.GrpcNameResolverProvider;
import com.navercorp.pinpoint.profiler.metadata.MetaDataType;
import com.navercorp.pinpoint.profiler.monitor.metric.gc.JvmGcType;
import com.navercorp.pinpoint.profiler.receiver.ProfilerCommandLocatorBuilder;
import com.navercorp.pinpoint.profiler.sender.grpc.AgentGrpcDataSender;
import com.navercorp.pinpoint.profiler.sender.grpc.ReconnectExecutor;
import com.navercorp.pinpoint.profiler.sender.grpc.SimpleStreamState;
import com.navercorp.pinpoint.profiler.sender.grpc.SpanGrpcDataSender;
import com.navercorp.pinpoint.profiler.sender.grpc.StreamState;
import com.navercorp.pinpoint.profiler.util.AgentInfoFactory;

public final class PinpointSpanExporter implements SpanExporter {

    private static final ThrottlingLogger logger =
        new ThrottlingLogger(Logger.getLogger(PinpointSpanExporter.class.getName()));

    private static final String AGENT_CHANNEL_FACTORY = "agentChannelFactory";
    private static final String SPAN_CHANNEL_FACTORY = "spanChannelFactory";

    private final long agentStartTime = System.currentTimeMillis();

    private final ScheduledExecutorService scheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor();

    private final ReconnectExecutor reconnectExecutor =
        new ReconnectExecutor(scheduledExecutorService);

    private final NameResolverProvider nameResolverProvider =
        new GrpcNameResolverProvider(new DnsExecutorServiceProvider()).get();

    private final String agentId;

    private final String agentName;

    private final String applicationName;

    private final GrpcTransportConfig grpcTransportConfig;

    private final HeaderFactory headerFactory;

    private final AgentInfoSender agentInfoSender;

    private final SpanGrpcDataSender spanGrpcDataSender;

    private final TraceRootFactory traceRootFactory;

    public PinpointSpanExporter(final String agentId,
                                final String agentName,
                                final String applicationName,
                                final GrpcTransportConfig grpcTransportConfig) {

        this.agentId = Objects.requireNonNull(agentId, "agentId cannot be null");
        this.agentName = Objects.requireNonNull(agentName, "agentName cannot be null");
        this.applicationName = Objects.requireNonNull(applicationName, "applicationName cannot be  null");
        this.grpcTransportConfig = Objects.requireNonNull(grpcTransportConfig, "grpcTransportConfig cannot be  null");

        TraceIdFactory traceIdFactory = new DefaultTraceIdFactory(agentId, agentStartTime);
        this.traceRootFactory = new DefaultTraceRootFactory(agentId, traceIdFactory);

        this.headerFactory = new AgentHeaderFactory(
            agentId,
            agentName,
            applicationName,
            ServiceType.UNDEFINED.getCode(),
            agentStartTime
        );

        this.agentInfoSender = createAgentInfoSender();
        this.agentInfoSender.start();

        this.spanGrpcDataSender = createSpanGrpcDataSender();
    }

    private AgentInfoSender createAgentInfoSender() {
        ChannelFactory agentChannelFactory = createAgentChannelFactory();

        AgentGrpcDataSender<MetaDataType> agentGrpcDataSender =
            new AgentGrpcDataSender<>(
                grpcTransportConfig.getAgentCollectorIp(),
                grpcTransportConfig.getAgentCollectorPort(),
                grpcTransportConfig.getAgentSenderExecutorQueueSize(),
                new GrpcAgentInfoMessageConverter(),
                reconnectExecutor,
                scheduledExecutorService,
                agentChannelFactory,
                new ProfilerCommandLocatorBuilder().build());

        AgentInformationProvider agentInformationProvider =
            new AgentInformationProvider(
                agentId,
                agentName,
                applicationName,
                Boolean.TRUE,
                agentStartTime,
                ServiceType.UNDEFINED);

        JvmInformation jvmInformation = new JvmInformation(
            JvmUtils.getSystemProperty(SystemPropertyKey.JAVA_VERSION),
            JvmGcType.UNKNOWN);

        ServerMetaDataRegistryService serverMetaDataRegistryService = new DefaultServerMetaDataRegistryService(
            Collections.emptyList()
        );
        serverMetaDataRegistryService.setServerName(PinpointConstants.SERVICE_NAME);

        AgentInfoFactory agentInfoFactory = new AgentInfoFactory(
            agentInformationProvider.createAgentInformation(),
            serverMetaDataRegistryService,
            jvmInformation
        );

        return new AgentInfoSender.Builder(agentGrpcDataSender, agentInfoFactory).build();
    }

    private SpanGrpcDataSender createSpanGrpcDataSender() {
        ChannelFactory spanChannelFactory = createSpanChannelFactory();

        GrpcSpanMessageConverter messageConverter =
            new GrpcSpanMessageConverter(
                agentId,
                ServiceType.UNDEFINED.getCode(),
                new GrpcSpanProcessorV2()
            );

        StreamState streamState =
            new SimpleStreamState(
                grpcTransportConfig.getSpanClientOption().getLimitCount(),
                grpcTransportConfig.getSpanClientOption().getLimitTime()
            );

        return new SpanGrpcDataSender(
            grpcTransportConfig.getSpanCollectorIp(),
            grpcTransportConfig.getSpanCollectorPort(),
            grpcTransportConfig.getSpanSenderExecutorQueueSize(),
            messageConverter,
            reconnectExecutor,
            spanChannelFactory,
            streamState
        );
    }

    private ChannelFactory createAgentChannelFactory() {
        ChannelFactoryBuilder channelFactoryBuilder =
            new DefaultChannelFactoryBuilder(AGENT_CHANNEL_FACTORY);
        channelFactoryBuilder.setHeaderFactory(headerFactory);
        channelFactoryBuilder.setNameResolverProvider(nameResolverProvider);
        channelFactoryBuilder.setSslOption(grpcTransportConfig.getSslOption());
        channelFactoryBuilder.setClientOption(grpcTransportConfig.getAgentClientOption());
        channelFactoryBuilder.setExecutorQueueSize(grpcTransportConfig.getAgentChannelExecutorQueueSize());

        return channelFactoryBuilder.build();
    }

    private ChannelFactory createSpanChannelFactory() {
        ChannelFactoryBuilder channelFactoryBuilder =
            new DefaultChannelFactoryBuilder(SPAN_CHANNEL_FACTORY);
        channelFactoryBuilder.setHeaderFactory(headerFactory);
        channelFactoryBuilder.setNameResolverProvider(nameResolverProvider);
        channelFactoryBuilder.setSslOption(grpcTransportConfig.getSslOption());
        channelFactoryBuilder.setClientOption(grpcTransportConfig.getSpanClientOption());
        channelFactoryBuilder.setExecutorQueueSize(grpcTransportConfig.getSpanChannelExecutorQueueSize());

        return channelFactoryBuilder.build();
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        if (spans.isEmpty()) {
            return CompletableResultCode.ofSuccess();
        }
        for (SpanData spanData : spans) {
            if (spanData == null) {
                continue;
            }
            try {
                if (!spanGrpcDataSender.send(toSpan(spanData))) {
                    return CompletableResultCode.ofFailure();
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to export span", e);
                return CompletableResultCode.ofFailure();
            }
        }
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        agentInfoSender.stop();
        spanGrpcDataSender.stop();
        try {
            scheduledExecutorService.shutdown();
        } catch (Exception ignored) {
            // ignored
        }
        try {
            reconnectExecutor.close();
        } catch (Exception ignored) {
            // ignored
        }
        return CompletableResultCode.ofSuccess();
    }

    private Span toSpan(SpanData spanData) {
        TraceRoot traceRoot = traceRootFactory.newTraceRoot(hex16StringToLong(spanData.getSpanId()));
        if (spanData.getStatus() != null) {
            traceRoot.getShared().setStatusCode(spanData.getStatus().getStatusCode().ordinal());
        }

        long startTimestamp = toEpochMicros(spanData.getStartEpochNanos());
        long endTimestamp = toEpochMicros(spanData.getEndEpochNanos());

        Span span = new Span(traceRoot);
        span.setStartTime(startTimestamp);
        span.setElapsedTime((int) (endTimestamp - startTimestamp));
        span.setServiceType(ServiceType.UNDEFINED.getCode());

        Optional.ofNullable(spanData.getAttributes()).ifPresent(attributes ->
            attributes.forEach((key, value) -> span.addAnnotation(toAnnotation(key, value))));

        Optional.ofNullable(spanData.getParentSpanContext()).ifPresent(parentSpanContext -> {
            if (parentSpanContext.isValid()) {
                // ignore
            }
        });

        InstrumentationLibraryInfo instrumentationLibraryInfo = spanData.getInstrumentationLibraryInfo();

        if (CollectionUtils.isNotEmpty(spanData.getEvents())) {
            span.setSpanEventList(spanData.getEvents().stream().map(this::toSpanEvent)
                .collect(Collectors.toList()));
        }

        return span;
    }

    private static Annotation<?> toAnnotation(AttributeKey<?> key, Object value) {
        AttributeType type = key.getType();
        switch (type) {
            case STRING:
            case BOOLEAN:
            case LONG:
            case DOUBLE:
            case STRING_ARRAY:
            case BOOLEAN_ARRAY:
            case LONG_ARRAY:
            case DOUBLE_ARRAY:
                return Annotations.of(API.getCode(), value);
        }
        throw new IllegalStateException("Unknown attribute type: " + type);
    }

    private SpanEvent toSpanEvent(EventData eventData) {
        SpanEvent spanEvent = new SpanEvent();
        spanEvent.setServiceType(ServiceType.UNDEFINED.getCode());
        spanEvent.setEndPoint(eventData.getName());
        eventData.getAttributes().forEach((key, value) -> spanEvent.addAnnotation(toAnnotation(key, value)));
        spanEvent.setElapsedTime((int) toEpochMicros(eventData.getEpochNanos()));
        return spanEvent;
    }

    private static long toEpochMicros(long epochNanos) {
        return NANOSECONDS.toMicros(epochNanos);
    }

    private static long hex16StringToLong(String hex16String) {
        CharSequence charSequence = new StringBuilder().append(hex16String);
        return OtelEncodingUtils.isValidBase16String(charSequence)
            ? OtelEncodingUtils.longFromBase16String(charSequence, 0)
            : hex16String.hashCode();
    }
}
