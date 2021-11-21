package org.apache.eventmesh.client.http.producer;

import org.apache.eventmesh.client.http.AbstractLiteClient;
import org.apache.eventmesh.client.http.EventMeshRetObj;
import org.apache.eventmesh.client.http.conf.LiteClientConfig;
import org.apache.eventmesh.client.http.http.HttpUtil;
import org.apache.eventmesh.client.http.http.RequestParam;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.io.IOException;

import com.google.common.base.Preconditions;

import io.netty.handler.codec.http.HttpMethod;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class EventMeshMessageProducer extends AbstractLiteClient implements EventMeshProtocolProducer<EventMeshMessage> {

    public EventMeshMessageProducer(LiteClientConfig liteClientConfig) throws EventMeshException {
        super(liteClientConfig);
    }

    @Override
    public void publish(EventMeshMessage message) throws EventMeshException {
        validateEventMeshMessage(message);
        RequestParam requestParam = buildCommonPostParam(message)
            .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.MSG_SEND_ASYNC.getRequestCode());

        String target = selectEventMesh();
        try {
            String response = HttpUtil.post(httpClient, target, requestParam);
            EventMeshRetObj ret = JsonUtils.deserialize(response, EventMeshRetObj.class);

            if (ret.getRetCode() != EventMeshRetCode.SUCCESS.getRetCode()) {
                throw new EventMeshException(ret.getRetCode(), ret.getRetMsg());
            }
        } catch (Exception exception) {
            throw new EventMeshException(String.format("Publish message error, target:%s", target), exception);
        }
    }

    @Override
    public EventMeshMessage request(EventMeshMessage message, long timeout) throws EventMeshException {
        validateEventMeshMessage(message);
        RequestParam requestParam = buildCommonPostParam(message)
            .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.MSG_SEND_SYNC.getRequestCode())
            .setTimeout(timeout);

        String target = selectEventMesh();

        try {
            String response = HttpUtil.post(httpClient, target, requestParam);
            EventMeshRetObj ret = JsonUtils.deserialize(response, EventMeshRetObj.class);
            if (ret.getRetCode() == EventMeshRetCode.SUCCESS.getRetCode()) {
                return transformMessage(ret);
            }
            throw new EventMeshException(ret.getRetCode(), ret.getRetMsg());
        } catch (Exception e) {
            throw new EventMeshException(String.format("Request message error, target:%s", target), e);
        }
    }

    @Override
    public void request(EventMeshMessage message, RRCallback rrCallback, long timeout) throws EventMeshException {
        validateEventMeshMessage(message);

        RequestParam requestParam = buildCommonPostParam(message)
            .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.MSG_SEND_SYNC.getRequestCode())
            .setTimeout(timeout);

        String target = selectEventMesh();
        RRCallbackResponseHandlerAdapter adapter = new RRCallbackResponseHandlerAdapter(message, rrCallback, timeout);
        try {
            HttpUtil.post(httpClient, null, target, requestParam, adapter);
        } catch (IOException e) {
            throw new EventMeshException(String.format("Request message error, target:%s", target), e);
        }
    }

    private void validateEventMeshMessage(EventMeshMessage message) {
        Preconditions.checkNotNull(message, "eventMeshMessage invalid");
        Preconditions.checkNotNull(message.getTopic(), "eventMeshMessage[topic] invalid");
        Preconditions.checkNotNull(message.getContent(), "eventMeshMessage[content] invalid");
    }

    private String selectEventMesh() {
        if (liteClientConfig.isUseTls()) {
            return Constants.HTTPS_PROTOCOL_PREFIX + eventMeshServerSelector.select();
        } else {
            return Constants.HTTP_PROTOCOL_PREFIX + eventMeshServerSelector.select();
        }
    }

    private RequestParam buildCommonPostParam(EventMeshMessage message) {
        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam
            .addHeader(ProtocolKey.ClientInstanceKey.ENV, liteClientConfig.getEnv())
            .addHeader(ProtocolKey.ClientInstanceKey.IDC, liteClientConfig.getIdc())
            .addHeader(ProtocolKey.ClientInstanceKey.IP, liteClientConfig.getIp())
            .addHeader(ProtocolKey.ClientInstanceKey.PID, liteClientConfig.getPid())
            .addHeader(ProtocolKey.ClientInstanceKey.SYS, liteClientConfig.getSys())
            .addHeader(ProtocolKey.ClientInstanceKey.USERNAME, liteClientConfig.getUserName())
            .addHeader(ProtocolKey.ClientInstanceKey.PASSWD, liteClientConfig.getPassword())
            .addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion())
            .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
            .addBody(SendMessageRequestBody.PRODUCERGROUP, liteClientConfig.getProducerGroup())
            // todo: set message to content is better
            .addBody(SendMessageRequestBody.TOPIC, message.getTopic())
            .addBody(SendMessageRequestBody.CONTENT, message.getContent())
            .addBody(SendMessageRequestBody.TTL, message.getProp(Constants.EVENTMESH_MESSAGE_CONST_TTL))
            .addBody(SendMessageRequestBody.BIZSEQNO, message.getBizSeqNo())
            .addBody(SendMessageRequestBody.UNIQUEID, message.getUniqueId());
        return requestParam;
    }

    private EventMeshMessage transformMessage(EventMeshRetObj retObj) {
        SendMessageResponseBody.ReplyMessage replyMessage = JsonUtils.deserialize(retObj.getRetMsg(),
            SendMessageResponseBody.ReplyMessage.class);
        return EventMeshMessage.builder()
            .content(replyMessage.body)
            .prop(replyMessage.properties)
            .topic(replyMessage.topic).build();
    }
}
