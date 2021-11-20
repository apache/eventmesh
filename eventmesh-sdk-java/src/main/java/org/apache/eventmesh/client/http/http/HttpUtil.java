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

package org.apache.eventmesh.client.http.http;

import org.apache.eventmesh.common.Constants;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import io.netty.handler.codec.http.HttpMethod;

public class HttpUtil {

    public static Logger logger = LoggerFactory.getLogger(HttpUtil.class);

    public static String post(CloseableHttpClient client,
                              String uri,
                              RequestParam requestParam) throws Exception {
        final ResponseHolder responseHolder = new ResponseHolder();
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        post(client, null, uri, requestParam, new ResponseHandler<String>() {
            @Override
            public String handleResponse(HttpResponse response) throws IOException {
                responseHolder.response =
                    EntityUtils.toString(response.getEntity(), Charset.forName(Constants.DEFAULT_CHARSET));
                countDownLatch.countDown();
                if (logger.isDebugEnabled()) {
                    logger.debug("{}", responseHolder);
                }
                return responseHolder.response;
            }
        });

        try {
            countDownLatch.await(requestParam.getTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
        }

        return responseHolder.response;
    }

    public static String post(CloseableHttpClient client,
                              HttpHost forwardAgent,
                              String uri,
                              RequestParam requestParam) throws Exception {
        final ResponseHolder responseHolder = new ResponseHolder();
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        post(client, forwardAgent, uri, requestParam, new ResponseHandler<String>() {
            @Override
            public String handleResponse(HttpResponse response) throws IOException {
                responseHolder.response =
                    EntityUtils.toString(response.getEntity(), Charset.forName(Constants.DEFAULT_CHARSET));
                countDownLatch.countDown();
                if (logger.isDebugEnabled()) {
                    logger.debug("{}", responseHolder);
                }
                return responseHolder.response;
            }
        });

        try {
            countDownLatch.await(requestParam.getTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            // ignore
        }

        return responseHolder.response;
    }

    public static void post(CloseableHttpClient client,
                            HttpHost forwardAgent,
                            String uri,
                            RequestParam requestParam,
                            ResponseHandler<String> responseHandler) throws IOException {
        Preconditions.checkState(client != null, "client can't be null");
        Preconditions.checkState(StringUtils.isNotBlank(uri), "uri can't be null");
        Preconditions.checkState(requestParam != null, "requestParam can't be null");
        Preconditions.checkState(responseHandler != null, "responseHandler can't be null");
        Preconditions.checkState(requestParam.getHttpMethod() == HttpMethod.POST, "invalid requestParam httpMethod");

        HttpPost httpPost = new HttpPost(uri);

        //header
        if (MapUtils.isNotEmpty(requestParam.getHeaders())) {
            for (Map.Entry<String, String> entry : requestParam.getHeaders().entrySet()) {
                httpPost.addHeader(entry.getKey(), entry.getValue());
            }
        }

        //body
        if (MapUtils.isNotEmpty(requestParam.getBody())) {
            List<NameValuePair> pairs = new ArrayList<>();
            for (Map.Entry<String, String> entry : requestParam.getBody().entrySet()) {
                pairs.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
            }
            httpPost.setEntity(new UrlEncodedFormEntity(pairs, Constants.DEFAULT_CHARSET));
        }

        //ttl
        RequestConfig.Builder configBuilder = RequestConfig.custom();
        configBuilder.setSocketTimeout(Integer.parseInt(String.valueOf(requestParam.getTimeout())))
            .setConnectTimeout(Integer.parseInt(String.valueOf(requestParam.getTimeout())))
            .setConnectionRequestTimeout(Integer.parseInt(String.valueOf(requestParam.getTimeout())));

        if (forwardAgent != null) {
            configBuilder.setProxy(forwardAgent);
        }

        httpPost.setConfig(configBuilder.build());

        if (logger.isDebugEnabled()) {
            logger.debug("{}", httpPost);
        }

        client.execute(httpPost, responseHandler);
    }

    public static void get(CloseableHttpClient client,
                           HttpHost forwardAgent,
                           String uri,
                           RequestParam requestParam,
                           ResponseHandler<String> responseHandler) throws Exception {
        Preconditions.checkState(client != null, "client can't be null");
        Preconditions.checkState(StringUtils.isNotBlank(uri), "uri can't be null");
        Preconditions.checkState(requestParam != null, "requestParam can't be null");
        Preconditions.checkState(requestParam.getHttpMethod() == HttpMethod.GET, "invalid requestParam httpMethod");

        //body
        if (MapUtils.isNotEmpty(requestParam.getQueryParamsMap())) {
            uri = uri + "?" + requestParam.getQueryParams();
        }

        HttpGet httpGet = new HttpGet(uri);

        //header
        if (MapUtils.isNotEmpty(requestParam.getHeaders())) {
            for (Map.Entry<String, String> entry : requestParam.getHeaders().entrySet()) {
                httpGet.addHeader(entry.getKey(), entry.getValue());
            }
        }

        //ttl
        RequestConfig.Builder configBuilder = RequestConfig.custom();
        configBuilder.setSocketTimeout(Integer.parseInt(String.valueOf(requestParam.getTimeout())))
            .setConnectTimeout(Integer.parseInt(String.valueOf(requestParam.getTimeout())))
            .setConnectionRequestTimeout(Integer.parseInt(String.valueOf(requestParam.getTimeout())));

        if (forwardAgent != null) {
            configBuilder.setProxy(forwardAgent);
        }

        httpGet.setConfig(configBuilder.build());

        if (logger.isDebugEnabled()) {
            logger.debug("{}", httpGet);
        }

        client.execute(httpGet, responseHandler);
    }

    public static String get(CloseableHttpClient client,
                             String url,
                             RequestParam requestParam) throws Exception {
        final ResponseHolder responseHolder = new ResponseHolder();
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        get(client, null, url, requestParam, new ResponseHandler<String>() {
            @Override
            public String handleResponse(HttpResponse response) throws IOException {
                responseHolder.response =
                    EntityUtils.toString(response.getEntity(), Charset.forName(Constants.DEFAULT_CHARSET));
                countDownLatch.countDown();
                if (logger.isDebugEnabled()) {
                    logger.debug("{}", responseHolder);
                }
                return responseHolder.response;
            }
        });

        try {
            countDownLatch.await(requestParam.getTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
        }

        return responseHolder.response;
    }

    public static String get(CloseableHttpClient client,
                             HttpHost forwardAgent,
                             String url,
                             RequestParam requestParam) throws Exception {
        final ResponseHolder responseHolder = new ResponseHolder();
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        get(client, forwardAgent, url, requestParam, new ResponseHandler<String>() {
            @Override
            public String handleResponse(HttpResponse response) throws IOException {
                responseHolder.response =
                    EntityUtils.toString(response.getEntity(), Charset.forName(Constants.DEFAULT_CHARSET));
                countDownLatch.countDown();
                if (logger.isDebugEnabled()) {
                    logger.debug("{}", responseHolder);
                }
                return responseHolder.response;
            }
        });

        try {
            countDownLatch.await(requestParam.getTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
        }

        return responseHolder.response;
    }

    public static class ResponseHolder {
        public String response;

        @Override
        public String toString() {
            return "ResponseHolder=" + response + "";
        }
    }
}
