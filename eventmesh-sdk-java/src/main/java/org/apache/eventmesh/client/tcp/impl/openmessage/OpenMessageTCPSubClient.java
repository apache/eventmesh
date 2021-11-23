package org.apache.eventmesh.client.tcp.impl.openmessage;

import org.apache.eventmesh.client.tcp.EventMeshTCPSubClient;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;

import io.openmessaging.api.Message;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenMessageTCPSubClient implements EventMeshTCPSubClient<Message> {
    @Override
    public void init() throws EventMeshException {

    }

    @Override
    public void heartbeat() throws EventMeshException {

    }

    @Override
    public void reconnect() throws EventMeshException {

    }

    @Override
    public void subscribe(String topic, SubscriptionMode subscriptionMode, SubscriptionType subscriptionType)
        throws EventMeshException {

    }

    @Override
    public void unsubscribe() throws EventMeshException {

    }

    @Override
    public void listen() throws EventMeshException {

    }

    @Override
    public void registerBusiHandler(ReceiveMsgHook<Message> handler) throws EventMeshException {

    }

    @Override
    public void close() throws EventMeshException {

    }
}
