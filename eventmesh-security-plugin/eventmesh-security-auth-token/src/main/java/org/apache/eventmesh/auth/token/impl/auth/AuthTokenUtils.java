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

package org.apache.eventmesh.auth.token.impl.auth;

import org.apache.eventmesh.api.acl.AclProperties;
import org.apache.eventmesh.api.exception.AclException;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.common.utils.TypeUtils;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;
import java.util.Set;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;

public class AuthTokenUtils {

    public static void authTokenByPublicKey(AclProperties aclProperties) {

        String token = aclProperties.getToken();
        if (StringUtils.isNotBlank(token)) {
            if (!authAccess(aclProperties)) {
                throw new AclException("group:" + aclProperties.getExtendedField("group ") + " has no auth to access the topic:"
                    + aclProperties.getTopic());
            }
            String publicKeyUrl = getPublicKeyUrl();
            validateToken(token, publicKeyUrl, aclProperties);
        } else {
            throw new AclException("invalid token!");
        }
    }

    public static void helloTaskAuthTokenByPublicKey(AclProperties aclProperties) {
        String token = aclProperties.getToken();
        if (StringUtils.isNotBlank(token)) {
            validateToken(token, getPublicKeyUrl(), aclProperties);
        } else {
            throw new AclException("invalid token!");
        }
    }

    public static boolean authAccess(AclProperties aclProperties) {

        String topic = aclProperties.getTopic();

        Object topics = aclProperties.getExtendedField("topics");

        if (!(topics instanceof Set)) {
            return false;
        }

        Set<String> groupTopics = TypeUtils.castSet(topics, String.class);

        return groupTopics.contains(topic);
    }

    private static String getPublicKeyUrl() {
        String publicKeyUrl = null;
        for (String key : ConfigurationContextUtil.KEYS) {
            CommonConfiguration commonConfiguration = ConfigurationContextUtil.get(key);
            if (null == commonConfiguration) {
                continue;
            }
            if (StringUtils.isBlank(commonConfiguration.getEventMeshSecurityPublickey())) {
                throw new AclException("publicKeyUrl cannot be null");
            }
            publicKeyUrl = commonConfiguration.getEventMeshSecurityPublickey();
        }
        return publicKeyUrl;
    }

    private static void validateToken(String token, String publicKeyUrl, AclProperties aclProperties) {
        String sub;
        token = token.replace("Bearer ", "");
        byte[] validationKeyBytes;
        try {
            validationKeyBytes = Files.readAllBytes(Paths.get(Objects.requireNonNull(publicKeyUrl)));
            X509EncodedKeySpec spec = new X509EncodedKeySpec(validationKeyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            Key validationKey = kf.generatePublic(spec);
            JwtParser signedParser = Jwts.parserBuilder().setSigningKey(validationKey).build();
            Jwt<?, Claims> signJwt = signedParser.parseClaimsJws(token);
            sub = signJwt.getBody().get("sub", String.class);
            if (!sub.contains(aclProperties.getExtendedField("group").toString()) && !sub.contains("pulsar-admin")) {
                throw new AclException("group:" + aclProperties.getExtendedField("group ") + " has no auth to access eventMesh:"
                    + aclProperties.getTopic());
            }
        } catch (IOException e) {
            throw new AclException("public key read error!", e);
        } catch (NoSuchAlgorithmException e) {
            throw new AclException("no such RSA algorithm!", e);
        } catch (InvalidKeySpecException e) {
            throw new AclException("invalid public key spec!", e);
        } catch (JwtException e) {
            throw new AclException("invalid token!", e);
        }
    }

}
