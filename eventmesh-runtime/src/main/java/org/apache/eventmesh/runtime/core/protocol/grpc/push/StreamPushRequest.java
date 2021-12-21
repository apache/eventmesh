package org.apache.eventmesh.runtime.core.protocol.grpc.push;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshMessageWrapper;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupTopicConfig;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class StreamPushRequest extends AbstractPushRequest {

    private HandleMsgContext handleMsgContext;

    private Map<String, Set<AbstractPushRequest>> waitingRequests;

    private EventMeshGrpcConfiguration grpcConfiguration;

    private ConsumerGroupTopicConfig topicConfig;

    private Map<String, List<StreamObserver<EventMeshMessage>>> idcEmitters;

    private List<StreamObserver<EventMeshMessage>> totalEmitters;

    private volatile int startIdx;

    public StreamPushRequest(HandleMsgContext handleMsgContext, Map<String, Set<AbstractPushRequest>> waitingRequests) {
        super(handleMsgContext);
        this.handleMsgContext = handleMsgContext;
        this.waitingRequests = waitingRequests;
        this.grpcConfiguration = handleMsgContext.getEventMeshGrpcServer().getEventMeshGrpcConfiguration();
        this.topicConfig = handleMsgContext.getConsumeTopicConfig();
        this.idcEmitters = buildIdcEmitter(this.topicConfig.getIdcEmitters());
        this.totalEmitters = buildTotalEmitter();
        this.startIdx = RandomUtils.nextInt(0, totalEmitters.size());
    }

    @Override
    public void tryPushRequest() {
        StreamObserver<EventMeshMessage> eventEmitter = selectEmitter();

        CloudEvent event = CloudEventBuilder.from(handleMsgContext.getEvent())
            .withExtension(EventMeshConstants.REQ_EVENTMESH2C_TIMESTAMP,
                String.valueOf(System.currentTimeMillis()))
            .build();
        handleMsgContext.setEvent(event);

        try {
            String protocolType = Objects.requireNonNull(event.getExtension(Constants.PROTOCOL_TYPE)).toString();

            ProtocolAdaptor<ProtocolTransportObject> protocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);

            ProtocolTransportObject protocolTransportObject =
                protocolAdaptor.fromCloudEvent(handleMsgContext.getEvent());

            EventMeshMessage eventMeshMessage = ((EventMeshMessageWrapper) protocolTransportObject).getMessage();
            if (eventMeshMessage != null) {
                eventEmitter.onNext(eventMeshMessage);
            }
        } catch (Exception e) {
                e.printStackTrace();
        }

        complete();
        if (isComplete()) {
            handleMsgContext.finish();
        }
    }

    @Override
    public boolean retry() throws Exception {
        return false;
    }

    private StreamObserver<EventMeshMessage> selectEmitter() {
        List<StreamObserver<EventMeshMessage>> emitterList = MapUtils.getObject(idcEmitters, grpcConfiguration.eventMeshIDC, null);
        if (CollectionUtils.isNotEmpty(emitterList)) {
            return emitterList.get((startIdx + retryTimes) % emitterList.size());
        }
        if (CollectionUtils.isNotEmpty(totalEmitters)) {
            return totalEmitters.get((startIdx + retryTimes) % totalEmitters.size());
        }
        return null;
    }

    private Map<String, List<StreamObserver<EventMeshMessage>>> buildIdcEmitter(
        Map<String, Map<String, StreamObserver<EventMeshMessage>>> idcEmitterMap) {
        Map<String, List<StreamObserver<EventMeshMessage>>> result = new HashMap<>();
        for (Map.Entry<String, Map<String, StreamObserver<EventMeshMessage>>> entry : idcEmitterMap.entrySet()) {
            List<StreamObserver<EventMeshMessage>> emitterList = new LinkedList<>(entry.getValue().values());
            result.put(entry.getKey(), emitterList);
        }
        return result;
    }

    private List<StreamObserver<EventMeshMessage>> buildTotalEmitter() {
        List<StreamObserver<EventMeshMessage>> emitterList = new LinkedList<>();
        for (List<StreamObserver<EventMeshMessage>> emitters : this.idcEmitters.values()) {
            emitterList.addAll(emitters);
        }
        return emitterList;
    }
}
