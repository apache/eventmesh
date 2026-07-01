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

package org.apache.eventmesh.connector.http.util;

public class HttpUtils {

    /**
     * Checks if the status code represents a successful response (2xx).
     *
     * @param statusCode the HTTP status code to check
     * @return true if the status code is 2xx, false otherwise
     */
    public static boolean is2xxSuccessful(int statusCode) {
        int seriesCode = statusCode / 100;
        return seriesCode == 2;
    }
}
