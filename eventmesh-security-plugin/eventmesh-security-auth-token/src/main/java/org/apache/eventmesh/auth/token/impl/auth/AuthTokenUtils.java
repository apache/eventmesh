package org.apache.eventmesh.auth.token.impl.auth;

import org.apache.eventmesh.api.acl.AclProperties;
import org.apache.eventmesh.api.exception.AclException;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;

public class AuthTokenUtils {

    public static void authTokenByPublicKey(AclProperties aclProperties) {
        String publicKeyUrl = "";
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
        String token = aclProperties.getToken();
        if (StringUtils.isNotBlank(token)) {
            token = token.replace("Bearer ", "");
            byte[] validationKeyBytes = new byte[0];
            try {
                validationKeyBytes = Files.readAllBytes(Paths.get(publicKeyUrl));
                X509EncodedKeySpec spec = new X509EncodedKeySpec(validationKeyBytes);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                Key validationKey = kf.generatePublic(spec);
                JwtParser signedParser = Jwts.parserBuilder().setSigningKey(validationKey).build();
                signedParser.parseClaimsJws(token);
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

}
