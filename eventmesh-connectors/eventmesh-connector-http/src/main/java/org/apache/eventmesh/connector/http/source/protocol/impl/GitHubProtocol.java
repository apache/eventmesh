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

package org.apache.eventmesh.connector.http.source.protocol.impl;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.config.connector.http.SourceConnectorConfig;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.connector.http.common.SynchronizedCircularFifoQueue;
import org.apache.eventmesh.connector.http.source.data.CommonResponse;
import org.apache.eventmesh.connector.http.source.data.WebhookRequest;
import org.apache.eventmesh.connector.http.source.protocol.Protocol;
import org.apache.eventmesh.connector.http.source.protocol.WebhookConstants;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.BodyHandler;

import com.alibaba.fastjson2.JSONObject;

import lombok.extern.slf4j.Slf4j;


/**
 * GitHub Protocol. This class represents the GitHub webhook protocol.
 */
@Slf4j
public class GitHubProtocol implements Protocol {

    // Protocol name
    public static final String PROTOCOL_NAME = "GitHub";

    private static final String H_MAC_SHA_265 = "HmacSHA256";

    private static final String SECRET_KEY = "secret";

    private String contentType = "application/json";

    private String secret;


    /**
     * Initialize the protocol.
     *
     * @param sourceConnectorConfig source connector config
     */
    @Override
    public void initialize(SourceConnectorConfig sourceConnectorConfig) {
        // Initialize the protocol
        Map<String, String> extraConfig = sourceConnectorConfig.getExtraConfig();
        // set the secret, if it is not set, throw an exception
        this.secret = extraConfig.get(SECRET_KEY);
        if (StringUtils.isBlank(this.secret)) {
            throw new EventMeshException("The secret is required for GitHub protocol.");
        }
        // if the content-type is not set, use the default value
        this.contentType = extraConfig.getOrDefault("contentType", contentType);
    }

    /**
     * Handle the protocol message for GitHub.
     *
     * @param route route
     * @param queue queue info
     */
    @Override
    public void setHandler(Route route, SynchronizedCircularFifoQueue<Object> queue) {
        route.method(HttpMethod.POST)
            .handler(BodyHandler.create())
            .handler(ctx -> {
                // Get the payload and headers
                String payloadStr = ctx.body().asString(Constants.DEFAULT_CHARSET.toString());
                MultiMap headers = ctx.request().headers();

                // validate the content type
                if (!StringUtils.contains(headers.get("Content-Type"), contentType)) {
                    String errorMsg = String.format("content-type is invalid, please check the content-type. received content-type: %s",
                        headers.get("Content-Type"));
                    // Return Bad Request
                    ctx.fail(HttpResponseStatus.BAD_REQUEST.code(), new EventMeshException(errorMsg));
                    return;
                }

                // validate the signature
                String signature = headers.get(WebhookConstants.GITHUB_SIGNATURE_256);
                if (BooleanUtils.isFalse(validateSignature(signature, payloadStr, secret))) {
                    String errorMsg = String.format("signature is invalid, please check the secret. received signature: %s", signature);
                    // Return Bad Request
                    ctx.fail(HttpResponseStatus.BAD_REQUEST.code(), new EventMeshException(errorMsg));
                    return;
                }

                // if the content type is form data, convert it to json string
                if (StringUtils.contains(contentType, "application/x-www-form-urlencoded")
                    || StringUtils.contains(contentType, "multipart/form-data")) {
                    /*
                      Convert form data to json string. There are the following benefits:
                      1. Raw form data is not decoded, so it is not easy to process directly.
                      2. Converted to reduce storage space by more than 20 percent. Experimental result: 10329 bytes -> 7893 bytes.
                     */
                    JSONObject payloadObj = new JSONObject();
                    ctx.request().formAttributes().forEach(entry -> payloadObj.put(entry.getKey(), entry.getValue()));
                    payloadStr = payloadObj.toJSONString();
                }

                // Create and store the webhook request
                Map<String, String> headerMap = headers.entries().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                WebhookRequest webhookRequest = new WebhookRequest(PROTOCOL_NAME, ctx.request().absoluteURI(), headerMap, payloadStr);

                if (!queue.offer(webhookRequest)) {
                    throw new IllegalStateException("Failed to store the request.");
                }

                // Return 200 OK
                ctx.response()
                    .setStatusCode(HttpResponseStatus.OK.code())
                    .end(CommonResponse.success().toJsonStr());
            })
            .failureHandler(ctx -> {
                log.error("Failed to handle the request from github. ", ctx.failure());

                // Return Bad Response
                ctx.response()
                    .setStatusCode(ctx.statusCode())
                    .end(CommonResponse.base(ctx.failure().getMessage()).toJsonStr());
            });
    }


    /**
     * Validate the signature.
     *
     * @param signature signature
     * @param payload   payload
     * @param secret    secret
     * @return boolean
     */
    public boolean validateSignature(String signature, String payload, String secret) {
        String hash = WebhookConstants.GITHUB_HASH_265_PREFIX;
        try {
            Mac sha = Mac.getInstance(H_MAC_SHA_265);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(Constants.DEFAULT_CHARSET), H_MAC_SHA_265);
            sha.init(secretKey);
            byte[] bytes = sha.doFinal(payload.getBytes(Constants.DEFAULT_CHARSET));
            hash += byteArrayToHexString(bytes);
        } catch (Exception e) {
            throw new EventMeshException("Error occurred while validating the signature.", e);
        }

        return hash.equals(signature);
    }


    /**
     * Convert the byte array to hex string.
     *
     * @param bytes bytes
     * @return String
     */
    private String byteArrayToHexString(byte[] bytes) {
        if (bytes == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                // If the length is 1, append 0
                sb.append('0');
            }
            sb.append(hex);
        }

        return sb.toString();
    }


    /**
     * Convert the message to ConnectRecord.
     *
     * @param message message
     * @return ConnectRecord
     */
    @Override
    public ConnectRecord convertToConnectRecord(Object message) {
        WebhookRequest request = (WebhookRequest) message;
        Map<String, String> headers = request.getHeaders();

        // Create the ConnectRecord
        ConnectRecord connectRecord = new ConnectRecord(null, null, System.currentTimeMillis(), request.getPayload());
        connectRecord.addExtension("id", headers.get(WebhookConstants.GITHUB_DELIVERY));
        connectRecord.addExtension("topic", headers.get(WebhookConstants.GITHUB_EVENT));
        connectRecord.addExtension("source", headers.get(request.getProtocolName()));
        connectRecord.addExtension("type", headers.get(WebhookConstants.GITHUB_HOOK_INSTALLATION_TARGET_TYPE));
        return connectRecord;
    }


}
