
package com.webank.eventmesh.runtime.util;

import io.openmessaging.KeyValue;
import io.openmessaging.Message;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class OMSUtil {

    public static boolean isOMSHeader(String value) {
        for (Field field : Message.BuiltinKeys.class.getDeclaredFields()) {
            try {
                if (field.get(Message.BuiltinKeys.class).equals(value)) {
                    return true;
                }
            } catch (IllegalAccessException e) {
                return false;
            }
        }
        return false;
    }

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

    public static Map<String, String> getMessageProp(Message message){
        Properties p1 = convertKeyValue2Prop(message.sysHeaders());
        Properties p2 = convertKeyValue2Prop(message.userHeaders());
        return combineProp(p1, p2);
    }

}
