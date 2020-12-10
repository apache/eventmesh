package com.webank.api;

import io.openmessaging.producer.SendResult;

public interface SendCallback {

    void onSuccess(final SendResult sendResult);

    void onException(final Throwable e);

}
