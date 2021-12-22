package org.apache.eventmesh.runtime.core.protocol.grpc.push;

import io.grpc.stub.StreamObserver;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.StreamTopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StreamPushRequest extends AbstractPushRequest {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Map<String, List<StreamObserver<EventMeshMessage>>> idcEmitters;

    private final List<StreamObserver<EventMeshMessage>> totalEmitters;

    private final int startIdx;

    public StreamPushRequest(HandleMsgContext handleMsgContext, Map<String, Set<AbstractPushRequest>> waitingRequests) {
        super(handleMsgContext, waitingRequests);

        StreamTopicConfig topicConfig = (StreamTopicConfig) handleMsgContext.getConsumeTopicConfig();
        this.idcEmitters = buildIdcEmitter(topicConfig.getIdcEmitters());
        this.totalEmitters = buildTotalEmitter();
        this.startIdx = RandomUtils.nextInt(0, totalEmitters.size());
    }

    @Override
    public void tryPushRequest() {
        StreamObserver<EventMeshMessage> eventEmitter = selectEmitter();

        EventMeshMessage eventMeshMessage = getEventMeshMessage(event);
        if (eventMeshMessage == null) {
            return;
        }
        eventEmitter.onNext(eventMeshMessage);
        complete();
        finish();
    }

    private StreamObserver<EventMeshMessage> selectEmitter() {
        List<StreamObserver<EventMeshMessage>> emitterList = MapUtils.getObject(idcEmitters,
            eventMeshGrpcConfiguration.eventMeshIDC, null);
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
