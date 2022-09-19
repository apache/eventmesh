package org.apache.eventmesh.runtime.core.protocol.amqp.processor;

import com.rabbitmq.client.LongString;
import org.apache.eventmesh.runtime.boot.EventMeshAmqpServer;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ProtocolFrame;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ProtocolVersion;

import java.util.Map;

public class AmqpConnection  extends AmqpHandler{


    public AmqpConnection(EventMeshAmqpServer amqpServer) {
        super(amqpServer);
    }

    @Override
    public void close() {

    }

    @Override
    public void receiveConnectionStartOk(Map<String, Object> clientProperties, String mechanism, LongString response, String locale) {

    }

    @Override
    public void receiveConnectionSecureOk(LongString response) {

    }

    @Override
    public void receiveConnectionTuneOk(int channelMax, long frameMax, int heartbeat) {

    }

    @Override
    public void receiveConnectionOpen(String virtualHost, String capabilities, boolean insist) {

    }

    @Override
    public void receiveChannelOpen(int channelId) {

    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        return null;
    }

    @Override
    public ChannelMethodProcessor getChannelMethodProcessor(int channelId) {
        return null;
    }

    @Override
    public void receiveConnectionClose(int replyCode, String replyText, int classId, int methodId) {

    }

    @Override
    public void receiveConnectionCloseOk() {

    }

    @Override
    public void receiveHeartbeat() {

    }

    @Override
    public void receiveProtocolHeader(ProtocolFrame protocolFrame) {

    }

    @Override
    public void setCurrentMethod(int classId, int methodId) {

    }

    @Override
    public boolean ignoreAllButCloseOk() {
        return false;
    }
}
