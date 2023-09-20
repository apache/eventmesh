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

import static org.apache.eventmesh.trace.pinpoint.common.PinpointConstants.REQ_IP;
import static org.apache.eventmesh.trace.pinpoint.common.PinpointConstants.UNKNOWN_REQ_IP;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.trace.api.common.EventMeshTraceConstants;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.grpc.NameResolverProvider;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.internal.OtelEncodingUtils;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.internal.ThrottlingLogger;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

import com.navercorp.pinpoint.bootstrap.context.SpanId;
import com.navercorp.pinpoint.bootstrap.context.TraceId;
import com.navercorp.pinpoint.common.trace.AnnotationKey;
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
import com.navercorp.pinpoint.profiler.context.DefaultServerMetaDataRegistryService;
import com.navercorp.pinpoint.profiler.context.ServerMetaDataRegistryService;
import com.navercorp.pinpoint.profiler.context.Span;
import com.navercorp.pinpoint.profiler.context.SpanEvent;
import com.navercorp.pinpoint.profiler.context.annotation.Annotations;
import com.navercorp.pinpoint.profiler.context.compress.GrpcSpanProcessorV2;
import com.navercorp.pinpoint.profiler.context.grpc.GrpcAgentInfoMessageConverter;
import com.navercorp.pinpoint.profiler.context.grpc.GrpcSpanMessageConverter;
import com.navercorp.pinpoint.profiler.context.grpc.config.GrpcTransportConfig;
import com.navercorp.pinpoint.profiler.context.id.DefaultTraceId;
import com.navercorp.pinpoint.profiler.context.id.DefaultTraceRoot;
import com.navercorp.pinpoint.profiler.context.id.TraceRoot;
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

    private static final ThrottlingLogger THROTTLING_LOGGER =
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

    public PinpointSpanExporter(final String agentId,
                                final String agentName,
                                final String applicationName,
                                final GrpcTransportConfig grpcTransportConfig) {

        this.agentId = Objects.requireNonNull(agentId, "agentId cannot be null");
        this.agentName = Objects.requireNonNull(agentName, "agentName cannot be null");
        this.applicationName = Objects.requireNonNull(applicationName, "applicationName cannot be  null");
        this.grpcTransportConfig = Objects.requireNonNull(grpcTransportConfig, "grpcTransportConfig cannot be  null");

        this.headerFactory = new AgentHeaderFactory(
                agentId,
                agentName,
                applicationName,
                ServiceType.UNDEFINED.getCode(),
                agentStartTime);

        this.agentInfoSender = createAgentInfoSender();
        this.agentInfoSender.start();

        this.spanGrpcDataSender = createSpanGrpcDataSender();
    }

    private AgentInfoSender createAgentInfoSender() {
        final ChannelFactory agentChannelFactory = createAgentChannelFactory();

        final AgentGrpcDataSender<MetaDataType> agentGrpcDataSender =
                new AgentGrpcDataSender<>(
                        grpcTransportConfig.getAgentCollectorIp(),
                        grpcTransportConfig.getAgentCollectorPort(),
                        grpcTransportConfig.getAgentSenderExecutorQueueSize(),
                        new GrpcAgentInfoMessageConverter(),
                        reconnectExecutor,
                        scheduledExecutorService,
                        agentChannelFactory,
                        new ProfilerCommandLocatorBuilder().build());

        final AgentInformationProvider agentInformationProvider =
                new AgentInformationProvider(
                        agentId,
                        agentName,
                        applicationName,
                        true,
                        agentStartTime,
                        ServiceType.STAND_ALONE);

        final JvmInformation jvmInformation = new JvmInformation(
                JvmUtils.getSystemProperty(SystemPropertyKey.JAVA_VERSION),
                JvmGcType.UNKNOWN);

        final ServerMetaDataRegistryService serverMetaDataRegistryService = new DefaultServerMetaDataRegistryService(
                Collections.emptyList());
        serverMetaDataRegistryService.setServerName(EventMeshTraceConstants.SERVICE_NAME);

        final AgentInfoFactory agentInfoFactory = new AgentInfoFactory(
                agentInformationProvider.createAgentInformation(),
                serverMetaDataRegistryService,
                jvmInformation);

        return new AgentInfoSender.Builder(agentGrpcDataSender, agentInfoFactory).build();
    }

    private SpanGrpcDataSender createSpanGrpcDataSender() {
        final ChannelFactory spanChannelFactory = createSpanChannelFactory();

        final GrpcSpanMessageConverter messageConverter =
                new GrpcSpanMessageConverter(
                        agentId,
                        ServiceType.STAND_ALONE.getCode(),
                        new GrpcSpanProcessorV2());

        final StreamState streamState =
                new SimpleStreamState(
                        grpcTransportConfig.getSpanClientOption().getLimitCount(),
                        grpcTransportConfig.getSpanClientOption().getLimitTime());

        return new SpanGrpcDataSender(
                grpcTransportConfig.getSpanCollectorIp(),
                grpcTransportConfig.getSpanCollectorPort(),
                grpcTransportConfig.getSpanSenderExecutorQueueSize(),
                messageConverter,
                reconnectExecutor,
                spanChannelFactory,
                streamState);
    }

    private ChannelFactory createAgentChannelFactory() {
        final ChannelFactoryBuilder channelFactoryBuilder =
                new DefaultChannelFactoryBuilder(AGENT_CHANNEL_FACTORY);
        channelFactoryBuilder.setHeaderFactory(headerFactory);
        channelFactoryBuilder.setNameResolverProvider(nameResolverProvider);
        channelFactoryBuilder.setSslOption(grpcTransportConfig.getSslOption());
        channelFactoryBuilder.setClientOption(grpcTransportConfig.getAgentClientOption());
        channelFactoryBuilder.setExecutorQueueSize(grpcTransportConfig.getAgentChannelExecutorQueueSize());

        return channelFactoryBuilder.build();
    }

    private ChannelFactory createSpanChannelFactory() {
        final ChannelFactoryBuilder channelFactoryBuilder =
                new DefaultChannelFactoryBuilder(SPAN_CHANNEL_FACTORY);
        channelFactoryBuilder.setHeaderFactory(headerFactory);
        channelFactoryBuilder.setNameResolverProvider(nameResolverProvider);
        channelFactoryBuilder.setSslOption(grpcTransportConfig.getSslOption());
        channelFactoryBuilder.setClientOption(grpcTransportConfig.getSpanClientOption());
        channelFactoryBuilder.setExecutorQueueSize(grpcTransportConfig.getSpanChannelExecutorQueueSize());

        return channelFactoryBuilder.build();
    }

    @Override
    public CompletableResultCode export(final Collection<SpanData> spans) {
        if (spans.isEmpty()) {
            return CompletableResultCode.ofSuccess();
        }
        for (final SpanData spanData : spans) {
            if (spanData == null) {
                continue;
            }
            try {
                if (!spanGrpcDataSender.send(toSpan(spanData))) {
                    return CompletableResultCode.ofFailure();
                }
            } catch (Exception e) {
                THROTTLING_LOGGER.log(Level.WARNING, "Failed to export span", e);
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

    private Span toSpan(final SpanData spanData) {
        final long startTimestamp = toMillis(spanData.getStartEpochNanos());
        final long transactionId = hex32StringToLong(spanData.getTraceId());
        final long spanId = hex16StringToLong(spanData.getSpanId());
        final long[] parentSpanId = {SpanId.NULL};

        Optional.ofNullable(spanData.getParentSpanContext()).ifPresent(parentSpanContext -> {
            if (parentSpanContext.isValid()) {
                parentSpanId[0] = hex16StringToLong(parentSpanContext.getSpanId());
            }
        });

        final TraceId traceId = new DefaultTraceId(agentId, startTimestamp, transactionId, parentSpanId[0], spanId,
                (short) spanData.getKind().ordinal());

        final TraceRoot traceRoot = new DefaultTraceRoot(traceId, this.agentId, startTimestamp, transactionId);

        final Span span = new Span(traceRoot);

        final StatusData statusData = spanData.getStatus();
        if (statusData != null) {
            Optional.ofNullable(traceRoot.getShared()).ifPresent(shared -> {
                shared.setRpcName(spanData.getName());
                shared.setEndPoint(getEndpoint(spanData.getResource()));
                if (!StatusCode.OK.equals(statusData.getStatusCode())) {
                    shared.maskErrorCode(statusData.getStatusCode().ordinal());
                    span.setExceptionInfo(statusData.getStatusCode().ordinal(), statusData.getDescription());
                }
            });
        }

        span.setStartTime(startTimestamp);
        final long endTimestamp = toMillis(spanData.getEndEpochNanos());
        span.setElapsedTime((int) (endTimestamp - startTimestamp));
        span.setServiceType(ServiceType.STAND_ALONE.getCode());
        span.setRemoteAddr(UNKNOWN_REQ_IP);

        Optional.ofNullable(spanData.getAttributes())
                .ifPresent(attributes -> {
                    span.addAnnotation(Annotations.of(AnnotationKey.HTTP_PARAM_ENTITY.getCode(),
                            JsonUtils.toJSONString(attributes)));
                    attributes.forEach((key, value) -> {
                        if (REQ_IP.equals(key.getKey())) {
                            span.setRemoteAddr(String.valueOf(value));
                        }
                    });
                });

        if (CollectionUtils.isNotEmpty(spanData.getEvents())) {
            final AtomicInteger sequence = new AtomicInteger();
            span.setSpanEventList(spanData.getEvents().stream().map(event -> {
                final SpanEvent spanEvent = toSpanEvent(event);
                spanEvent.setSequence(sequence.getAndIncrement());
                return spanEvent;
            }).collect(Collectors.toList()));
        }

        return span;
    }

    private SpanEvent toSpanEvent(final EventData eventData) {
        final SpanEvent spanEvent = new SpanEvent();
        spanEvent.setServiceType(ServiceType.INTERNAL_METHOD.getCode());
        spanEvent.setEndPoint(eventData.getName());
        spanEvent.addAnnotation(Annotations.of(AnnotationKey.HTTP_PARAM_ENTITY.getCode(),
                JsonUtils.toJSONString(eventData.getAttributes())));
        spanEvent.setElapsedTime((int) toMillis(eventData.getEpochNanos()));
        return spanEvent;
    }

    private static long toMillis(final long epochNanos) {
        return NANOSECONDS.toMillis(epochNanos);
    }

    private static long hex32StringToLong(final String hex32String) {
        final CharSequence charSequence = new StringBuilder().append(hex32String);
        return OtelEncodingUtils.isValidBase16String(charSequence)
                ? OtelEncodingUtils.longFromBase16String(charSequence, 0)
                        & OtelEncodingUtils.longFromBase16String(charSequence, 16)
                : hex32String.hashCode();
    }

    private static long hex16StringToLong(final String hex16String) {
        final CharSequence charSequence = new StringBuilder().append(hex16String);
        return OtelEncodingUtils.isValidBase16String(charSequence)
                ? OtelEncodingUtils.longFromBase16String(charSequence, 0)
                : hex16String.hashCode();
    }

    private static String getEndpoint(final Resource resource) {
        if (resource == null) {
            return null;
        }

        final Attributes resourceAttributes = resource.getAttributes();
        Objects.requireNonNull(resourceAttributes, "resourceAttributes can not be null");

        String serviceNameValue = resourceAttributes.get(ResourceAttributes.SERVICE_NAME);
        if (serviceNameValue == null) {
            serviceNameValue = Resource.getDefault().getAttributes().get(ResourceAttributes.SERVICE_NAME);
        }
        return serviceNameValue + Constants.POUND + IPUtils.getLocalAddress();
    }
}
