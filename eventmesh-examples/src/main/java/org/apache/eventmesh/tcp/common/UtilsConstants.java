package org.apache.eventmesh.tcp.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * @author wangguoqiang wrote on 2022/09/15 18:25
 * @version 1.0
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UtilsConstants {

    public static final String ENV = "test";
    public static final String HOST = "127.0.0.1";
    public static final Integer PASSWORD_LENGTH = 8;
    public static final String USER_NAME = "PU4283";
    public static final String GROUP = "EventmeshTestGroup";
    public static final String PATH = "/data/app/umg_proxy";
    public static final Integer PORT_1 = 8362;
    public static final Integer PORT_2 = 9362;
    public static final String SUB_SYSTEM_1 = "5023";
    public static final String SUB_SYSTEM_2 = "5017";
    public static final Integer PID_1 = 32893;
    public static final Integer PID_2 = 42893;
    public static final String VERSION = "2.0.11";
    public static final String IDC = "FT";
    /**
     * PROPERTY KEY NAME .
     */
    public static final String MSG_TYPE = "msgtype";
    public static final String TTL = "ttl";
    public static final String KEYS = "keys";
    public static final String REPLY_TO = "replyto";
    public static final String PROPERTY_MESSAGE_REPLY_TO = "propertymessagereplyto";
    public static final String CONTENT = "content";


}
