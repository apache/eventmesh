package org.apache.eventmesh.client.tcp.impl.cloudevent;

import org.apache.eventmesh.client.tcp.EventMeshTCPSubClient;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;

import io.cloudevents.CloudEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * CloudEvent TCP subscribe client implementation.
 */
@Slf4j
public class CloudEventTCPSubClient implements EventMeshTCPSubClient<CloudEvent> {

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
    public void registerBusiHandler(ReceiveMsgHook<CloudEvent> handler) throws EventMeshException {

    }

    @Override
    public void close() throws EventMeshException{

    }
}
