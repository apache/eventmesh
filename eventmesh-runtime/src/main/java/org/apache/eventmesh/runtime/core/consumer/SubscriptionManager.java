package org.apache.eventmesh.runtime.core.consumer;

import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.Client;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class SubscriptionManager {
    private final ConcurrentHashMap<String /**group*/, ConsumerGroupConf> localConsumerGroupMapping =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String /**group@topic*/, List<Client>> localClientInfoMapping =
            new ConcurrentHashMap<>();

    public ConcurrentHashMap<String, ConsumerGroupConf> getLocalConsumerGroupMapping() {
        return localConsumerGroupMapping;
    }

    public ConcurrentHashMap<String, List<Client>> getLocalClientInfoMapping() {
        return localClientInfoMapping;
    }
}
