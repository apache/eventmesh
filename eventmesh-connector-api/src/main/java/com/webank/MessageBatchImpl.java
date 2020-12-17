package com.webank;

import com.webank.api.message.MessageBatch;
import io.openmessaging.KeyValue;
import io.openmessaging.Message;
import io.openmessaging.OMS;
import io.openmessaging.exception.OMSMessageFormatException;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MessageBatchImpl implements MessageBatch {

    private KeyValue sysHeaders;
    private KeyValue userHeaders;
    private List<Message> messages;
    private byte[] body;
    private static final String RETRY_GROUP_TOPIC_PREFIX = "%RETRY%";

    public MessageBatchImpl() {
        this.sysHeaders = OMS.newKeyValue();
        this.userHeaders = OMS.newKeyValue();
    }

    @Override
    public MessageBatch generateFromList(Collection<Message> messages) {
        assert messages != null;
        assert messages.size() > 0;
        List<Message> messageList = new ArrayList<Message>(messages.size());
        Message first = null;
        for (Message message : messages) {
            if (message.userHeaders().getInt("DELAY", 0) > 0) {
                throw new UnsupportedOperationException("TimeDelayLevel in not supported for batching");
            }
            if (message.sysHeaders().getString(BuiltinKeys.DESTINATION).startsWith(RETRY_GROUP_TOPIC_PREFIX)) {
                throw new UnsupportedOperationException("Retry Group is not supported for batching");
            }
            if (first == null) {
                first = message;
            } else {
                if (!first.sysHeaders().getString(BuiltinKeys.DESTINATION).equals(message.sysHeaders().getString(BuiltinKeys.DESTINATION))) {
                    throw new UnsupportedOperationException("The topic of the messages in one batch should be the same");
                }
                if (isWaitStoreMsgOK(first) != isWaitStoreMsgOK(message)) {
                    throw new UnsupportedOperationException("The waitStoreMsgOK of the messages in one batch should the same");
                }
            }
            messageList.add(message);
        }
        MessageBatchImpl messageBatch = new MessageBatchImpl();
        messageBatch.setMessages(messageList);

        messageBatch.sysHeaders().put(BuiltinKeys.DESTINATION, first.sysHeaders().getString(BuiltinKeys.DESTINATION));
        messageBatch.userHeaders().put("WAIT", String.valueOf(isWaitStoreMsgOK(first)));
        return messageBatch;
    }

    @Override
    public <T> T getBody(Class<T> type) throws OMSMessageFormatException {
        if (type == byte[].class) {
            return (T)body;
        }

        throw new OMSMessageFormatException("", "Cannot assign byte[] to " + type.getName());
    }

    @Override
    public MessageBatch setBody(byte[] body) {
        this.body = body;
        return this;
    }

    @Override
    public KeyValue sysHeaders() {
        return sysHeaders;
    }

    @Override
    public KeyValue userHeaders() {
        return userHeaders;
    }

    @Override
    public Message putSysHeaders(String key, int value) {
        sysHeaders.put(key, value);
        return this;
    }

    @Override
    public Message putSysHeaders(String key, long value) {
        sysHeaders.put(key, value);
        return this;
    }

    @Override
    public Message putSysHeaders(String key, double value) {
        sysHeaders.put(key, value);
        return this;
    }

    @Override
    public Message putSysHeaders(String key, String value) {
        sysHeaders.put(key, value);
        return this;
    }

    @Override
    public Message putUserHeaders(String key, int value) {
        userHeaders.put(key, value);
        return this;
    }

    @Override
    public Message putUserHeaders(String key, long value) {
        userHeaders.put(key, value);
        return this;
    }

    @Override
    public Message putUserHeaders(String key, double value) {
        userHeaders.put(key, value);
        return this;
    }

    @Override
    public Message putUserHeaders(String key, String value) {
        userHeaders.put(key, value);
        return this;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public List<Message> getMessages() {
        return messages;
    }

    @Override
    public byte[] encode() {
        return new byte[0];
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    private boolean isWaitStoreMsgOK(Message message){
        String result = message.userHeaders().getString("WAIT");
        if (null == result)
            return true;

        return Boolean.parseBoolean(result);
    }

    public static byte[] encodeMessages(List<Message> messages) {
        //TO DO refactor, accumulate in one buffer, avoid copies
        List<byte[]> encodedMessages = new ArrayList<byte[]>(messages.size());
        int allSize = 0;
        for (Message message : messages) {
            byte[] tmp = encodeMessage(message);
            encodedMessages.add(tmp);
            allSize += tmp.length;
        }
        byte[] allBytes = new byte[allSize];
        int pos = 0;
        for (byte[] bytes : encodedMessages) {
            System.arraycopy(bytes, 0, allBytes, pos, bytes.length);
            pos += bytes.length;
        }
        return allBytes;
    }

    public static byte[] encodeMessage(Message message) {
        //only need flag, body, properties
        byte[] body = message.getBody(byte[].class);
        int bodyLen = body.length;
//        String properties = messageProperties2String(message.getProperties());
//        byte[] propertiesBytes = properties.getBytes(CHARSET_UTF8);
//        //note properties length must not more than Short.MAX
//        short propertiesLength = (short) propertiesBytes.length;
//        int sysFlag = message.getFlag();
        int storeSize = 4 // 1 TOTALSIZE
                + 4 // 2 MAGICCOD
                + 4 // 3 BODYCRC
                + 4 // 4 FLAG
                + 4 + bodyLen;// 4 BODY
//                + 2 + propertiesLength;
        ByteBuffer byteBuffer = ByteBuffer.allocate(storeSize);
//        // 1 TOTALSIZE
//        byteBuffer.putInt(storeSize);
//
//        // 2 MAGICCODE
//        byteBuffer.putInt(0);
//
//        // 3 BODYCRC
//        byteBuffer.putInt(0);
//
//        // 4 FLAG
//        int flag = message.getFlag();
//        byteBuffer.putInt(flag);
//
//        // 5 BODY
//        byteBuffer.putInt(bodyLen);
//        byteBuffer.put(body);
//
//        // 6 properties
//        byteBuffer.putShort(propertiesLength);
//        byteBuffer.put(propertiesBytes);

        return byteBuffer.array();
    }

}
