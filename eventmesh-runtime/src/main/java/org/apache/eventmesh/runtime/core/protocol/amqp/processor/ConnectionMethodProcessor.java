package org.apache.eventmesh.runtime.core.protocol.amqp.processor;

import com.rabbitmq.client.LongString;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ProtocolFrame;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ProtocolVersion;

import java.util.Map;

public interface ConnectionMethodProcessor {

    void receiveConnectionStartOk(Map<String, Object> clientProperties,
        String mechanism,
        LongString response,
        String locale);

    void receiveConnectionSecureOk(LongString response);

    void receiveConnectionTuneOk(int channelMax, long frameMax, int heartbeat);

    void receiveConnectionOpen(String virtualHost, String capabilities, boolean insist);

    void receiveChannelOpen(int channelId);

    ProtocolVersion getProtocolVersion();

    ChannelMethodProcessor getChannelMethodProcessor(int channelId);

    void receiveConnectionClose(int replyCode, String replyText, int classId, int methodId);

    void receiveConnectionCloseOk();

    void receiveHeartbeat();

    void receiveProtocolHeader(ProtocolFrame protocolFrame);

    void setCurrentMethod(int classId, int methodId);

    boolean ignoreAllButCloseOk();

}
