package org.apache.eventmesh.api;

import io.openmessaging.api.Message;

public interface RRCallback {

    public void onSuccess(Message msg);

    public void onException(Throwable e);

}
