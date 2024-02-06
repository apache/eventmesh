/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.common.utils;

import org.apache.eventmesh.common.Constants;

import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * NetUtils
 */
@Slf4j
public class NetUtils {

    /**
     * Transform the url form string to Map
     *
     * @param formData
     * @return url parameters map
     */
    public static Map<String, String> formData2Dic(String formData) {
        if (StringUtils.isBlank(formData)) {
            return new HashMap<>();
        }
        final String[] items = formData.split(Constants.AND);
        Map<String, String> result = new HashMap<>(items.length);
        Arrays.stream(items).forEach(item -> {
            final String[] keyAndVal = item.split(Constants.EQ);
            if (keyAndVal.length == 2) {
                try {
                    final String key = URLDecoder.decode(keyAndVal[0], Constants.DEFAULT_CHARSET.name());
                    final String val = URLDecoder.decode(keyAndVal[1], Constants.DEFAULT_CHARSET.name());
                    result.put(key, val);
                } catch (UnsupportedEncodingException e) {
                    log.warn("formData2Dic:param decode failed...", e);
                }
            }
        });
        return result;
    }

    public static String addressToString(Collection<InetSocketAddress> clients) {
        if (clients.isEmpty()) {
            return "no session had been closed";
        }
        StringBuilder sb = new StringBuilder();
        for (InetSocketAddress addr : clients) {
            sb.append(addr).append(Constants.VERTICAL_LINE);
        }
        return sb.toString();
    }
}
