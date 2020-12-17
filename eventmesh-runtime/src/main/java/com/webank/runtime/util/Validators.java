package com.webank.runtime.util;

import com.webank.api.producer.MeshMQProducer;
import io.openmessaging.Message;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.MixAll;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Validators {

    public static final String VALID_PATTERN_STR = "^[%|a-zA-Z0-9_-]+$";
    public static final Pattern PATTERN = Pattern.compile(VALID_PATTERN_STR);
    public static final int CHARACTER_MAX_LENGTH = 255;

    /**
     * Validate message
     */
    public static void checkMessage(Message msg, MeshMQProducer defaultMQProducer)
            throws Exception {
        if (null == msg) {
            throw new RuntimeException("the message is null");
        }
        // topic
        Validators.checkTopic(msg.sysHeaders().getString(Message.BuiltinKeys.DESTINATION));

        // body
        if (null == msg.getBody(byte[].class)) {
            throw new RuntimeException("the message body is null");
        }

        if (0 == msg.getBody(byte[].class).length) {
            throw new RuntimeException("the message body length is zero");
        }

//        if (msg.getBody(byte[].class).length > defaultMQProducer.getMaxMessageSize()) {
//            throw new RuntimeException("the message body size over max value, MAX: " + defaultMQProducer.getMaxMessageSize());
//        }
    }

    /**
     * Validate topic
     */
    public static void checkTopic(String topic) throws Exception {
        if (StringUtils.isBlank(topic)) {
            throw new RuntimeException("The specified topic is blank", null);
        }

        if (!regularExpressionMatcher(topic, PATTERN)) {
            throw new RuntimeException(String.format(
                    "The specified topic[%s] contains illegal characters, allowing only %s", topic,
                    VALID_PATTERN_STR), null);
        }

        if (topic.length() > CHARACTER_MAX_LENGTH) {
            throw new RuntimeException("The specified topic is longer than topic max length 255.", null);
        }

        //whether the same with system reserved keyword
        if (topic.equals(MixAll.AUTO_CREATE_TOPIC_KEY_TOPIC)) {
            throw new RuntimeException(
                    String.format("The topic[%s] is conflict with AUTO_CREATE_TOPIC_KEY_TOPIC.", topic), null);
        }
    }

    /**
     * @return <tt>true</tt> if, and only if, the entire origin sequence matches this matcher's pattern
     */
    public static boolean regularExpressionMatcher(String origin, Pattern pattern) {
        if (pattern == null) {
            return true;
        }
        Matcher matcher = pattern.matcher(origin);
        return matcher.matches();
    }
}
