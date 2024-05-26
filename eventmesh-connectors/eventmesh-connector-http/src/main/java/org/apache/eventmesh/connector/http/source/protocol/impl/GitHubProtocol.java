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
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.connector.http.common.BoundedConcurrentQueue;
import org.apache.eventmesh.connector.http.source.config.SourceConnectorConfig;
import org.apache.eventmesh.connector.http.source.data.WebhookRequest;
import org.apache.eventmesh.connector.http.source.data.WebhookResponse;
import org.apache.eventmesh.connector.http.source.protocol.Protocol;
import org.apache.eventmesh.connector.http.source.protocol.WebhookConstants;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.BodyHandler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter.Feature;

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
        this.secret = extraConfig.get(SECRET_KEY);
        if (StringUtils.isBlank(this.secret)) {
            throw new EventMeshException("The secret is required for GitHub protocol.");
        }
    }

    /**
     * Handle the protocol message for GitHub.
     *
     * @param route     route
     * @param boundedQueue queue info
     */
    @Override
    public void setHandler(Route route, BoundedConcurrentQueue<Object> boundedQueue) {
        route.method(HttpMethod.POST)
            .handler(BodyHandler.create())
            .handler(ctx -> {
                // Get the payload
                String payload = ctx.body().asString(Constants.DEFAULT_CHARSET.toString());

                // Get the headers
                MultiMap headers = ctx.request().headers();
                // validate the signature
                String signature = headers.get(WebhookConstants.GITHUB_SIGNATURE_256);
                if (BooleanUtils.isFalse(validateSignature(signature, payload, secret))) {
                    String errorMsg = String.format("signature is invalid, please check the secret. received signature: %s", signature);
                    log.error(errorMsg);

                    WebhookResponse response = WebhookResponse.builder()
                        .msg(errorMsg)
                        .handleTime(LocalDateTime.now())
                        .build();

                    // Return 400 Bad Request
                    ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                        .send(JSON.toJSONString(response, Feature.WriteMapNullValue));
                    return;
                }

                // Create and store the webhook request
                Map<String, String> headerMap = new HashMap<>();
                headers.forEach(header -> headerMap.put(header.getKey(), header.getValue()));

                WebhookRequest webhookRequest = WebhookRequest.builder()
                    .protocolName(PROTOCOL_NAME)
                    .url(ctx.request().absoluteURI())
                    .headers(headerMap)
                    .payload(payload)
                    .build();

                // Add the webhook request to the queue, thread-safe
                boundedQueue.offerWithReplace(webhookRequest);

                // Return 200 OK
                WebhookResponse response = WebhookResponse.builder()
                    .msg("success")
                    .handleTime(LocalDateTime.now())
                    .build();
                ctx.response().setStatusCode(HttpResponseStatus.OK.code())
                    .send(JSON.toJSONString(response, Feature.WriteMapNullValue));

            })
            .failureHandler(ctx -> {
                log.error("Failed to handle the request from github.", ctx.failure());

                WebhookResponse response = WebhookResponse.builder()
                    .msg(ctx.failure().getMessage())
                    .handleTime(LocalDateTime.now())
                    .build();

                // Return Bad Response
                ctx.response().setStatusCode(ctx.statusCode())
                    .send(JSON.toJSONString(response, Feature.WriteMapNullValue));
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

        StringBuilder hexSb = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                // If the length is 1, append 0
                hexSb.append('0');
            }
            hexSb.append(hex);
        }

        return hexSb.toString();
    }


    /**
     * Convert the message to ConnectRecord.
     *
     * @param message message
     * @return ConnectRecord
     */
    @Override
    public ConnectRecord convertToConnectRecord(Object message) {
        return ((WebhookRequest) message).convertToConnectRecord();
    }


}
