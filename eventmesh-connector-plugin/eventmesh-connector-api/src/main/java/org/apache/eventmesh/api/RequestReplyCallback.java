package org.apache.eventmesh.api;

import io.cloudevents.CloudEvent;

public interface RequestReplyCallback {

    void onSuccess(CloudEvent event);

    void onException(Throwable e);
}
