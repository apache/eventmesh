package com.webank.api.message;

import io.openmessaging.Message;

import java.util.Collection;
import java.util.List;

public interface MessageBatch extends Message {
    /**
     * Sets the bytes message body.
     *
     * @param body the message body to be set
     */
    MessageBatch setBody(byte[] body);

    MessageBatch generateFromList(Collection<Message> messages);

    List<Message> getMessages();

    byte[] encode();
}
