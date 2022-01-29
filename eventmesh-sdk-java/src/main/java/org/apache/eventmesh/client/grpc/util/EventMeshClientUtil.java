package org.apache.eventmesh.client.grpc.util;

import com.fasterxml.jackson.core.type.TypeReference;
import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventMeshClientUtil {

    private static final Logger logger = LoggerFactory.getLogger(EventMeshClientUtil.class);

    public static RequestHeader buildHeader(EventMeshGrpcClientConfig clientConfig, String protocolType) {
        RequestHeader header = RequestHeader.newBuilder()
            .setEnv(clientConfig.getEnv())
            .setIdc(clientConfig.getIdc())
            .setIp(IPUtils.getLocalAddress())
            .setPid(Long.toString(ThreadUtils.getPID()))
            .setSys(clientConfig.getSys())
            .setLanguage(clientConfig.getLanguage())
            .setUsername(clientConfig.getUserName())
            .setPassword(clientConfig.getPassword())
            .setProtocolType(protocolType)
            .setProtocolDesc("grpc")
            // default CloudEvents version is V1
            .setProtocolVersion(SpecVersion.V1.toString())
            .build();
        return header;
    }

    public static <T> T buildMessage(SimpleMessage message, String protocolType) {
        String seq = message.getSeqNum();
        String uniqueId = message.getUniqueId();
        String content = message.getContent();

        // This is GRPC response message
        if (StringUtils.isEmpty(seq) && StringUtils.isEmpty(uniqueId)) {
            HashMap<String, String> response = JsonUtils.deserialize(content, new TypeReference<HashMap<String, String>>() {});
            return (T) response;
        }

        if (EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME.equals(protocolType)) {
            String contentType = message.getPropertiesOrDefault(ProtocolKey.CONTENT_TYPE, JsonFormat.CONTENT_TYPE);
            try {
                CloudEvent cloudEvent = EventFormatProvider.getInstance().resolveFormat(contentType)
                    .deserialize(content.getBytes(StandardCharsets.UTF_8));
                return (T) cloudEvent;
            } catch (Throwable t) {
                logger.warn("Error in building message. {}", t.getMessage());
                return null;
            }
        } else {
            EventMeshMessage eventMeshMessage = EventMeshMessage.builder()
                .content(content)
                .topic(message.getTopic())
                .bizSeqNo(seq)
                .uniqueId(uniqueId)
                .prop(message.getPropertiesMap())
                .build();
            return (T) eventMeshMessage;
        }
    }

    public static <T> SimpleMessage buildSimpleMessage(T message, EventMeshGrpcClientConfig clientConfig,
                                                       String protocolType) {
        if (EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME.equals(protocolType)) {
            CloudEvent cloudEvent = (CloudEvent) message;
            String contentType = StringUtils.isEmpty(cloudEvent.getDataContentType()) ? "application/cloudevents+json"
                : cloudEvent.getDataContentType();
            byte[] bodyByte = EventFormatProvider.getInstance().resolveFormat(contentType)
                .serialize(cloudEvent);
            String content = new String(bodyByte, StandardCharsets.UTF_8);
            String ttl = cloudEvent.getExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL) == null ? "4000"
                : cloudEvent.getExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL).toString();

            return SimpleMessage.newBuilder()
                .setHeader(EventMeshClientUtil.buildHeader(clientConfig, protocolType))
                .setProducerGroup(clientConfig.getProducerGroup())
                .setTopic(cloudEvent.getSubject())
                .setTtl(ttl)
                .setSeqNum(cloudEvent.getExtension(ProtocolKey.SEQ_NUM).toString())
                .setUniqueId(cloudEvent.getExtension(ProtocolKey.UNIQUE_ID).toString())
                .setContent(content)
                .putProperties(ProtocolKey.CONTENT_TYPE, contentType)
                .build();
        } else {
            EventMeshMessage eventMeshMessage = (EventMeshMessage) message;

            String ttl = eventMeshMessage.getProp(Constants.EVENTMESH_MESSAGE_CONST_TTL) == null ? "4000"
                : eventMeshMessage.getProp(Constants.EVENTMESH_MESSAGE_CONST_TTL);
            Map<String, String> props = eventMeshMessage.getProp() == null ? new HashMap<>() : eventMeshMessage.getProp();

            return SimpleMessage.newBuilder()
                .setHeader(EventMeshClientUtil.buildHeader(clientConfig, protocolType))
                .setProducerGroup(clientConfig.getProducerGroup())
                .setTopic(eventMeshMessage.getTopic())
                .setContent(eventMeshMessage.getContent())
                .setSeqNum(eventMeshMessage.getBizSeqNo())
                .setUniqueId(eventMeshMessage.getUniqueId())
                .setTtl(ttl)
                .putAllProperties(props)
                .build();
        }
    }

    public static <T> BatchMessage buildBatchMessages(List<T> messageList, EventMeshGrpcClientConfig clientConfig,
                                                      String protocolType) {
        if (EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME.equals(protocolType)) {
            List<CloudEvent> events = (List<CloudEvent>) messageList;
            BatchMessage.Builder messageBuilder = BatchMessage.newBuilder()
                .setHeader(EventMeshClientUtil.buildHeader(clientConfig, protocolType))
                .setProducerGroup(clientConfig.getProducerGroup())
                .setTopic(events.get(0).getSubject());

            for (CloudEvent event: events) {
                String contentType = StringUtils.isEmpty(event.getDataContentType()) ? "application/cloudevents+json"
                    : event.getDataContentType();
                byte[] bodyByte = EventFormatProvider.getInstance().resolveFormat(contentType)
                    .serialize(event);
                String content = new String(bodyByte, StandardCharsets.UTF_8);

                String ttl = event.getExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL) == null ? "4000"
                    : event.getExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL).toString();

                BatchMessage.MessageItem messageItem = BatchMessage.MessageItem.newBuilder()
                    .setContent(content)
                    .setTtl(ttl)
                    .setSeqNum(event.getExtension(ProtocolKey.SEQ_NUM).toString())
                    .setUniqueId(event.getExtension(ProtocolKey.UNIQUE_ID).toString())
                    .putProperties(ProtocolKey.CONTENT_TYPE, contentType)
                    .build();

                messageBuilder.addMessageItem(messageItem);
            }
            return messageBuilder.build();
        } else {
            BatchMessage.Builder messageBuilder = BatchMessage.newBuilder()
                .setHeader(EventMeshClientUtil.buildHeader(clientConfig, protocolType))
                .setProducerGroup(clientConfig.getProducerGroup());

            for (EventMeshMessage message : (List<EventMeshMessage>)messageList) {
                messageBuilder.setTopic(message.getTopic());
                BatchMessage.MessageItem item = BatchMessage.MessageItem.newBuilder()
                    .setContent(message.getContent())
                    .setUniqueId(message.getUniqueId())
                    .setSeqNum(message.getBizSeqNo())
                    .setTtl(message.getProp(Constants.EVENTMESH_MESSAGE_CONST_TTL))
                    .putAllProperties(message.getProp())
                    .build();
                messageBuilder.addMessageItem(item);
            }
            return messageBuilder.build();
        }
    }
}
