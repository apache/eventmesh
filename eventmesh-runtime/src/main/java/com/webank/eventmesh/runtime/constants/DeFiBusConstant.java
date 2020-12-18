package com.webank.eventmesh.runtime.constants;

//TODO
public class DeFiBusConstant {
    public static final String PROPERTY_MESSAGE_REPLY_TO = "REPLY_TO";  //requester clientId

    public static final String PROPERTY_RR_REQUEST_ID = "RR_REQUEST_UNIQ_ID";

    public static final String PROPERTY_MESSAGE_TTL = "TTL";    //timeout for request-response

    public static final String PROPERTY_MESSAGE_CLUSTER = "CLUSTER";    //cluster name

    public static final String PROPERTY_MESSAGE_BROKER = "BROKER";  //broker name where message stored

    public static final String REDIRECT = "REDIRECT";

    public static final String REDIRECT_FLAG = "REDIRECT_FLAG";

    public static final String PLUGIN_CLASS_NAME = "com.webank.defibus.broker.plugin.DeFiPluginMessageStore";

    public static final String RR_REPLY_TOPIC = "rr-reply-topic";   //post fix for reply topic

    public static final String KEY = "msgType";

    public static final String DEFAULT_TTL = "14400000";

    public static final String EXT_CONSUMER_GROUP = "ExtConsumerGroup";

    public static final String RMQ_SYS = "RMQ_SYS_";

    /**
     * msgType1: indicate the msg is broadcast message
     */
    public static final String DIRECT = "direct";

    /**
     * msgType2: msg of type except broadcast and reply
     */
    public static final String PERSISTENT = "persistent";

    /**
     * msgType3: indicate the msg is which consumer reply to producer
     */
    public static final String REPLY = "reply";

    public static final String INSTANCE_NAME_SEPERATER = "#";

    public static final String IDC_SEPERATER = "-";

    public static final String LEAVE_TIME = "LEAVE_TIME";            //leaveBrokerTime
    public static final String ARRIVE_TIME = "ARRIVE_TIME";
    public static final String STORE_TIME = "STORE_TIME";

}
