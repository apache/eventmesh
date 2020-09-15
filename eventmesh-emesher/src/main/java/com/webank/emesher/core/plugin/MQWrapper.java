package com.webank.emesher.core.plugin;

import com.webank.emesher.constants.ProxyConstants;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class MQWrapper {

    public static boolean useRocket = Boolean.FALSE;

    public static final String useRocketConf = System.getProperty(ProxyConstants.USE_ROCKET_PROPERTIES, System.getenv(ProxyConstants.USE_ROCKET_ENV));

    static {
        if (StringUtils.isNotBlank(useRocketConf)) {
            useRocket = Boolean.valueOf(useRocketConf);
        }
    }

    public AtomicBoolean started = new AtomicBoolean(Boolean.FALSE);

    public AtomicBoolean inited = new AtomicBoolean(Boolean.FALSE);



}
