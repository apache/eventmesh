package org.apache.eventmesh.client.tcp.impl.openmessage;

import io.openmessaging.api.Message;
import lombok.extern.slf4j.Slf4j;

import org.apache.eventmesh.client.tcp.EventMeshTCPPubClient;
import org.apache.eventmesh.client.tcp.common.AsyncRRCallback;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;

@Slf4j
public class OpenMessageTCPPubClient implements EventMeshTCPPubClient<Message> {

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
    public Package rr(Message msg, long timeout) throws EventMeshException {
        return null;
    }

    @Override
    public void asyncRR(Message msg, AsyncRRCallback callback, long timeout) throws EventMeshException {

    }

    @Override
    public Package publish(Message cloudEvent, long timeout) throws EventMeshException {
        return null;
    }

    @Override
    public void broadcast(Message cloudEvent, long timeout) throws EventMeshException {

    }

    @Override
    public void registerBusiHandler(ReceiveMsgHook<Message> handler) throws EventMeshException {

    }

    @Override
    public void close() throws EventMeshException {

    }
}
