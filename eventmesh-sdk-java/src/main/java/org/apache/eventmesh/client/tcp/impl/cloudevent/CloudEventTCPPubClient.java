package org.apache.eventmesh.client.tcp.impl.cloudevent;

import org.apache.eventmesh.client.tcp.EventMeshTCPPubClient;
import org.apache.eventmesh.client.tcp.common.AsyncRRCallback;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;

import io.cloudevents.CloudEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * A CloudEvent TCP publish client implementation.
 */
@Slf4j
public class CloudEventTCPPubClient implements EventMeshTCPPubClient<CloudEvent> {

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
    public Package rr(Package msg, long timeout) throws EventMeshException {
        return null;
    }

    @Override
    public void asyncRR(Package msg, AsyncRRCallback callback, long timeout) throws EventMeshException {

    }

    @Override
    public Package publish(Package msg, long timeout) throws EventMeshException {
        return null;
    }

    @Override
    public Package publish(CloudEvent cloudEvent, long timeout) throws EventMeshException {
        return null;
    }

    @Override
    public void broadcast(CloudEvent cloudEvent, long timeout) throws EventMeshException {

    }

    @Override
    public void broadcast(Package msg, long timeout) throws EventMeshException {

    }

    @Override
    public void registerBusiHandler(ReceiveMsgHook<CloudEvent> handler) throws EventMeshException {

    }

    @Override
    public UserAgent getUserAgent() {
        return null;
    }

    @Override
    public void close() throws EventMeshException {

    }
}
