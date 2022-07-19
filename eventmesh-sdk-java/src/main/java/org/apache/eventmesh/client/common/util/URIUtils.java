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

package org.apache.eventmesh.client.common.util;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;


public class URIUtils {

    private static final String PARAM_SEPARATOR = "=";

    private static final String DELIMITER_AND = "&";

    public static Map<String, String> getParams(URI uri) {
        Map<String, String> params = new HashMap<>();
        String query = uri.getQuery();
        if (query == null || query.length() == 0) {
            return params;
        }

        String[] pairs = query.split(DELIMITER_AND);
        if (pairs.length < 1) {
            return params;
        }

        for (String param : pairs) {
            String[] nameValue = param.split(PARAM_SEPARATOR);
            if (nameValue.length == 2) {
                params.put(nameValue[0], nameValue[1]);
            } else if (nameValue.length == 1) {
                params.put(nameValue[0], null);
            }
        }
        return params;
    }

}
