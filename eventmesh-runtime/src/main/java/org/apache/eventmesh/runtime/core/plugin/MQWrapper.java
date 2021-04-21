package org.apache.eventmesh.runtime.core.plugin;

import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class MQWrapper {

    public static final String EVENT_STORE_ROCKETMQ = "rocketmq";

    public static final String EVENT_STORE_DEFIBUS = "defibus";

    public static String CURRENT_EVENT_STORE = EVENT_STORE_DEFIBUS;

    public static final String EVENT_STORE_CONF = System.getProperty(EventMeshConstants.EVENT_STORE_PROPERTIES, System.getenv(EventMeshConstants.EVENT_STORE_ENV));

    static {
        if (StringUtils.isNotBlank(EVENT_STORE_CONF)) {
            CURRENT_EVENT_STORE = EVENT_STORE_CONF;
        }
    }

    public AtomicBoolean started = new AtomicBoolean(Boolean.FALSE);

    public AtomicBoolean inited = new AtomicBoolean(Boolean.FALSE);

}
