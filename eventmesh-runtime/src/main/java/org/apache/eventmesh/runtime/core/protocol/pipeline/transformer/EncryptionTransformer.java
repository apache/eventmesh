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

package org.apache.eventmesh.runtime.core.protocol.pipeline.transformer;

import org.apache.eventmesh.common.protocol.pipeline.PipelineContext;
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineTransformer;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * Encryption transformer — encrypts sensitive fields in event data.
 *
 * <p>Sensitive field names are configured via pipeline context attributes:
 * <pre>{@code
 *   ctx.setAttribute("EncryptionTransformer.sensitive", "password,token,secret,apiKey");
 * }</pre>
 *
 * <p>Uses AES-128 by default. Encryption key from pipeline context attribute.
 * Encrypted fields are base64-encoded and prefixed with "enc:" marker.
 */
@Slf4j
public class EncryptionTransformer implements PipelineTransformer {

    private static final String SENSITIVE_ATTR = "EncryptionTransformer.sensitive";
    private static final String KEY_ATTR = "EncryptionTransformer.key";
    private static final String DEFAULT_AES_KEY = "EventMeshEncrypt"; // 16 bytes for AES-128
    private static final String ENC_PREFIX = "enc:";

    @Override
    public String name() {
        return "encryption";
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    public CloudEvent transform(CloudEvent event, PipelineContext ctx) {
        Set<String> sensitive = getSensitiveFields(ctx);
        if (sensitive.isEmpty()) {
            return event; // no sensitive fields configured
        }

        if (event.getData() == null) {
            return event;
        }

        try {
            String content = new String(event.getData().toBytes(), StandardCharsets.UTF_8);
            String encrypted = encryptFields(content, sensitive, ctx);
            return CloudEventBuilder.from(event)
                    .withData(encrypted.getBytes(StandardCharsets.UTF_8))
                    .withExtension("eventmesh_encrypted_fields", String.join(",", sensitive))
                    .build();
        } catch (Exception e) {
            log.warn("EncryptionTransformer: failed to encrypt fields, pass-through", e);
            return event;
        }
    }

    Set<String> getSensitiveFields(PipelineContext ctx) {
        try {
            Object attr = ctx.getAttribute(SENSITIVE_ATTR);
            if (attr instanceof String && !((String) attr).isEmpty()) {
                return new HashSet<>(Arrays.asList(((String) attr).split(",")));
            }
        } catch (Exception e) {
            log.debug("EncryptionTransformer: no sensitive fields configured");
        }
        return Collections.emptySet();
    }

    String encryptFields(String json, Set<String> sensitive, PipelineContext ctx) {
        String key = (String) ctx.getAttribute(KEY_ATTR);
        if (key == null) key = DEFAULT_AES_KEY;

        Map<String, Object> dataMap = FieldMappingTransformer.parseJson(json);
        boolean modified = false;
        for (String field : sensitive) {
            Object value = dataMap.get(field);
            if (value instanceof String && !((String) value).startsWith(ENC_PREFIX)) {
                try {
                    String encrypted = encrypt((String) value, key);
                    dataMap.put(field, ENC_PREFIX + encrypted);
                    modified = true;
                    log.debug("EncryptionTransformer: encrypted field {}", field);
                } catch (Exception e) {
                    log.warn("EncryptionTransformer: failed to encrypt field {}", field, e);
                }
            }
        }
        if (!modified) return json;
        return FieldMappingTransformer.toJson(dataMap);
    }

    /** AES-128 encryption with base64 encoding */
    static String encrypt(String plaintext, String key) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(
            padKey(key).getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    static String padKey(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length >= 16) return key.substring(0, 16);
        StringBuilder padded = new StringBuilder(key);
        while (padded.length() < 16) padded.append('0');
        return padded.toString();
    }
}
