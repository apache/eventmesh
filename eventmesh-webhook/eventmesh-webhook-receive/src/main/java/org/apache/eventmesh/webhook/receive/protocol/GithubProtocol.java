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

package org.apache.eventmesh.webhook.receive.protocol;


import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.receive.ManufacturerProtocol;
import org.apache.eventmesh.webhook.receive.WebHookRequest;

import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GithubProtocol implements ManufacturerProtocol {

    private static final String MANU_FACTURER_NAME = "github";

    private static final String FROM_SIGNATURE = "x-hub-signature-256";

    private static final String MANU_FACTURER_EVENT_ID = "x-github-delivery";

    private static final String HASH = "sha256=";

    private static final String H_MAC_SHA = "HmacSHA256";

    private static final Char ZERO_CHAR = '0';

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public String getManufacturerName() {
        return MANU_FACTURER_NAME;
    }

    @Override
    public void execute(WebHookRequest webHookRequest, WebHookConfig webHookConfig, Map<String, String> header) throws Exception {

        String fromSignature = header.get(FROM_SIGNATURE);
        if (!isValid(fromSignature, webHookRequest.getData(), webHookConfig.getSecret())) {
            throw new Exception("webhook-GithubProtocol authenticate failed");
        }

        try {
            webHookRequest.setManufacturerEventId(header.get(MANU_FACTURER_EVENT_ID));
            webHookRequest.setManufacturerEventName(webHookConfig.getManufacturerEventName());
            webHookRequest.setManufacturerSource(getManufacturerName());
        } catch (Exception e) {
            throw new Exception("webhook-GithubProtocol parse failed");
        }
    }

    /**
     * Authentication
     *
     * @param fromSignature Signature received
     * @param data          data
     * @param secret        secret key
     * @return Authentication result
     */
    private Boolean isValid(String fromSignature, byte[] data, String secret) {
        String hash = HASH;
        try {
            Mac sha = Mac.getInstance(H_MAC_SHA);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(Constants.DEFAULT_CHARSET), H_MAC_SHA);
            sha.init(secretKey);
            byte[] bytes = sha.doFinal(data);
            hash += byteArrayToHexString(bytes);
        } catch (Exception e) {
            logger.error("Error HmacSHA256", e);
        }
        return hash.equals(fromSignature);
    }

    /**
     * byte array ->  hexadecimal character string
     *
     * @param b byte array
     * @return hexadecimal character string
     */
    private String byteArrayToHexString(byte[] b) {
        StringBuilder hs = new StringBuilder();
        String stmp;
        for (int n = 0; b != null && n < b.length; n++) {
            stmp = Integer.toHexString(b[n] & 0XFF);
            if (stmp.length() == 1) {
                hs.append(ZERO_CHAR);
            }
            hs.append(stmp);
        }
        return hs.toString().toLowerCase();
    }
}
