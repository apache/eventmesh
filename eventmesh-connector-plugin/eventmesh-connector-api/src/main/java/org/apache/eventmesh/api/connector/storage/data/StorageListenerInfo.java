package org.apache.eventmesh.api.connector.storage.data;

import org.apache.eventmesh.api.EventListener;

import lombok.Data;

@Data
public class StorageListenerInfo {

    private String topic;

    private EventListener listener;
}
