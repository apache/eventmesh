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

package org.apache.eventmesh.runtime.util;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class HttpTinyClient {

    public static HttpResult httpGet(String url, List<String> headers, List<String> paramValues,
        String encoding, long readTimeoutMs) throws IOException {
        String encodedContent = encodingParams(paramValues, encoding);
        url += (encodedContent == null) ? "" : ("?" + encodedContent);

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout((int) readTimeoutMs);
            conn.setReadTimeout((int) readTimeoutMs);
            setHeaders(conn, headers, encoding);

            conn.connect();
            int respCode = conn.getResponseCode();
            String resp = null;

            if (HttpURLConnection.HTTP_OK == respCode) {
                resp = IOUtils.toString(conn.getInputStream(), encoding);
            } else {
                resp = IOUtils.toString(conn.getErrorStream(), encoding);
            }
            return new HttpResult(respCode, resp);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String encodingParams(Collection<String> paramValues, String encoding)
        throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        if (paramValues == null) {
            return null;
        }

        for (Iterator<String> iter = paramValues.iterator(); iter.hasNext();) {
            sb.append(iter.next()).append("=");
            sb.append(URLEncoder.encode(iter.next(), encoding));
            if (iter.hasNext()) {
                sb.append("&");
            }
        }
        return sb.toString();
    }

    private static void setHeaders(HttpURLConnection conn, Collection<String> headers, String encoding) {
        if (headers != null) {
            for (Iterator<String> iter = headers.iterator(); iter.hasNext();) {
                conn.addRequestProperty(iter.next(), iter.next());
            }
        }
        conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=" + encoding);

        String ts = String.valueOf(System.currentTimeMillis());
        conn.addRequestProperty("Metaq-Client-RequestTS", ts);
    }

    /**
     * @return the http response of given http post request
     */
    public static HttpResult httpPost(String url, List<String> headers, List<String> paramValues,
        String encoding, long readTimeoutMs) throws IOException {
        String encodedContent = encodingParams(paramValues, encoding);

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout((int) readTimeoutMs);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            setHeaders(conn, headers, encoding);

            conn.getOutputStream().write(Objects.requireNonNull(encodedContent).getBytes(StandardCharsets.UTF_8));

            int respCode = conn.getResponseCode();
            String resp = HttpURLConnection.HTTP_OK == respCode ? IOUtils.toString(conn.getInputStream(), encoding)
                : IOUtils.toString(conn.getErrorStream(), encoding);

            return new HttpResult(respCode, resp);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static class HttpResult {

        private final transient int code;
        private final transient String content;

        public HttpResult(int code, String content) {
            this.code = code;
            this.content = content;
        }

        public int getCode() {
            return code;
        }

        public String getContent() {
            return content;
        }
    }
}
