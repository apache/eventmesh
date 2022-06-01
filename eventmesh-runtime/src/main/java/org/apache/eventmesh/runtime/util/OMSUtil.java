/*
 * Licensed to Apache Software Foundation (ASF) under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Apache Software Foundation (ASF) licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.eventmesh.runtime.util;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import io.openmessaging.api.Message;
import io.openmessaging.api.OMSBuiltinKeys;

public class OMSUtil {

    public static boolean isOMSHeader(String value) {
        for (Field field : OMSBuiltinKeys.class.getDeclaredFields()) {
            try {
                if (field.get(OMSBuiltinKeys.class).equals(value)) {
                    return true;
                }
            } catch (IllegalAccessException e) {
                return false;
            }
        }
        return false;
    }

//    public static Properties convertKeyValue2Prop(KeyValue keyValue){
//        Properties properties = new Properties();
//        for (String key : keyValue.keySet()){
//            properties.put(key, keyValue.getString(key));
//        }
//        return properties;
//    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> combineProp(Properties p1, Properties p2) {
        Properties properties = new Properties();
        properties.putAll(p1);
        properties.putAll(p2);

        return new HashMap<>((Map) properties);
    }

    public static Map<String, String> getMessageProp(Message message) {
        Properties p1 = message.getSystemProperties();
        Properties p2 = message.getUserProperties();
        return combineProp(p1, p2);
    }

}
