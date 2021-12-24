package org.apache.eventmesh.runtime.core.protocol.grpc.push;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.StreamTopicConfig;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class StreamPushRequest extends AbstractPushRequest {

    private final Logger messageLogger = LoggerFactory.getLogger("message");

    private final Map<String, List<EventEmitter<EventMeshMessage>>> idcEmitters;

    private final List<EventEmitter<EventMeshMessage>> totalEmitters;

    private final int startIdx;

    public StreamPushRequest(HandleMsgContext handleMsgContext, Map<String, Set<AbstractPushRequest>> waitingRequests) {
        super(handleMsgContext, waitingRequests);

        StreamTopicConfig topicConfig = (StreamTopicConfig) handleMsgContext.getConsumeTopicConfig();
        this.idcEmitters = topicConfig.getIdcEmitters();
        this.totalEmitters = topicConfig.getTotalEmitters();
        this.startIdx = RandomUtils.nextInt(0, totalEmitters.size());
    }

    @Override
    public void tryPushRequest() {
        if (eventMeshMessage == null) {
            return;
        }

        EventEmitter<EventMeshMessage> eventEmitter = selectEmitter();

        if (eventEmitter == null) {
            return;
        }
        this.lastPushTime = System.currentTimeMillis();

        eventMeshMessage.getPropertiesMap().put(EventMeshConstants.REQ_EVENTMESH2C_TIMESTAMP,
            String.valueOf(lastPushTime));
        try {
            long cost = System.currentTimeMillis() - lastPushTime;
            eventEmitter.getEmitter().onNext(eventMeshMessage);
            messageLogger.info(
                "message|eventMesh2client|emitter|topic={}|bizSeqNo={}"
                    + "|uniqueId={}|cost={}", eventMeshMessage.getTopic(),
                eventMeshMessage.getSeqNum(), eventMeshMessage.getUniqueId(), cost);
            complete();
        } catch (Throwable t) {
            long cost = System.currentTimeMillis() - lastPushTime;
            messageLogger.info(
                "message|eventMesh2client|exception|emitter|topic={}|bizSeqNo={}"
                    + "|uniqueId={}|cost={}", eventMeshMessage.getTopic(),
                eventMeshMessage.getSeqNum(), eventMeshMessage.getUniqueId(), cost);

            delayRetry();
        }
    }

    private EventEmitter<EventMeshMessage> selectEmitter() {
        List<EventEmitter<EventMeshMessage>> emitterList = MapUtils.getObject(idcEmitters,
            eventMeshGrpcConfiguration.eventMeshIDC, null);
        if (CollectionUtils.isNotEmpty(emitterList)) {
            return emitterList.get((startIdx + retryTimes) % emitterList.size());
        }
        if (CollectionUtils.isNotEmpty(totalEmitters)) {
            return totalEmitters.get((startIdx + retryTimes) % totalEmitters.size());
        }
        return null;
    }
}
