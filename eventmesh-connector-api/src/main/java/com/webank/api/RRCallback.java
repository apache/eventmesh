package com.webank.api;

import io.openmessaging.Message;

public interface RRCallback {

    public void onSuccess(Message msg);

    public void onException(Throwable e);

}
