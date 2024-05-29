package org.apache.eventmesh.registry;

import lombok.Getter;

import java.util.List;

public class NotifyEvent {

    public NotifyEvent(){

    }

    public NotifyEvent(List<RegisterServerInfo> instances) {
        this(instances, false);
    }

    public NotifyEvent(List<RegisterServerInfo> instances, boolean isIncrement) {
        this.isIncrement = isIncrement;
        this.instances = instances;
    }



    // means whether it is an increment data
    @Getter
    private boolean isIncrement;

    @Getter
    private List<RegisterServerInfo> instances;
}
