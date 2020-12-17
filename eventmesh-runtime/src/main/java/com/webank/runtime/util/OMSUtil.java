
package com.webank.runtime.util;

import io.openmessaging.KeyValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class OMSUtil {

    public static Properties convertKeyValue2Prop(KeyValue keyValue){
        Properties properties = new Properties();
        for (String key : keyValue.keySet()){
            properties.put(key, keyValue.getString(key));
        }
        return properties;
    }

    public static Map<String, String> combineProp(Properties p1, Properties p2){
        Properties properties = new Properties();
        properties.putAll(p1);
        properties.putAll(p2);

        return new HashMap<>((Map)properties);
    }

}
