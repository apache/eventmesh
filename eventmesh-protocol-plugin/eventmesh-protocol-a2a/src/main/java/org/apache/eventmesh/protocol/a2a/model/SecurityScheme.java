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

package org.apache.eventmesh.protocol.a2a.model;

import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Defines a security scheme that can be used to secure an agent's endpoints.
 * Discriminated union type based on OpenAPI 3.2 Security Scheme Object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurityScheme implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("apiKeySecurityScheme")
    private APIKeySecurityScheme apiKeySecurityScheme;

    @JsonProperty("httpAuthSecurityScheme")
    private HTTPAuthSecurityScheme httpAuthSecurityScheme;

    @JsonProperty("oauth2SecurityScheme")
    private OAuth2SecurityScheme oauth2SecurityScheme;

    @JsonProperty("openIdConnectSecurityScheme")
    private OpenIdConnectSecurityScheme openIdConnectSecurityScheme;

    @JsonProperty("mtlsSecurityScheme")
    private MutualTlsSecurityScheme mtlsSecurityScheme;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class APIKeySecurityScheme implements Serializable {

        private static final long serialVersionUID = 1L;

        private String description;
        private String location;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HTTPAuthSecurityScheme implements Serializable {

        private static final long serialVersionUID = 1L;

        private String description;
        private String scheme;

        @JsonProperty("bearerFormat")
        private String bearerFormat;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OAuth2SecurityScheme implements Serializable {

        private static final long serialVersionUID = 1L;

        private String description;
        private OAuthFlows flows;

        @JsonProperty("oauth2MetadataUrl")
        private String oauth2MetadataUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OpenIdConnectSecurityScheme implements Serializable {

        private static final long serialVersionUID = 1L;

        private String description;

        @JsonProperty("openIdConnectUrl")
        private String openIdConnectUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MutualTlsSecurityScheme implements Serializable {

        private static final long serialVersionUID = 1L;

        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OAuthFlows implements Serializable {

        private static final long serialVersionUID = 1L;

        @JsonProperty("authorizationCode")
        private AuthorizationCodeOAuthFlow authorizationCode;

        @JsonProperty("clientCredentials")
        private ClientCredentialsOAuthFlow clientCredentials;

        @JsonProperty("deviceCode")
        private DeviceCodeOAuthFlow deviceCode;

        @JsonProperty("implicit")
        private ImplicitOAuthFlow implicit;

        @JsonProperty("password")
        private PasswordOAuthFlow password;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AuthorizationCodeOAuthFlow implements Serializable {

        private static final long serialVersionUID = 1L;

        @JsonProperty("authorizationUrl")
        private String authorizationUrl;

        @JsonProperty("tokenUrl")
        private String tokenUrl;

        @JsonProperty("refreshUrl")
        private String refreshUrl;

        private Map<String, String> scopes;

        @JsonProperty("pkceRequired")
        private Boolean pkceRequired;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ClientCredentialsOAuthFlow implements Serializable {

        private static final long serialVersionUID = 1L;

        @JsonProperty("tokenUrl")
        private String tokenUrl;

        @JsonProperty("refreshUrl")
        private String refreshUrl;

        private Map<String, String> scopes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DeviceCodeOAuthFlow implements Serializable {

        private static final long serialVersionUID = 1L;

        @JsonProperty("deviceAuthorizationUrl")
        private String deviceAuthorizationUrl;

        @JsonProperty("tokenUrl")
        private String tokenUrl;

        @JsonProperty("refreshUrl")
        private String refreshUrl;

        private Map<String, String> scopes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImplicitOAuthFlow implements Serializable {

        private static final long serialVersionUID = 1L;

        @JsonProperty("authorizationUrl")
        private String authorizationUrl;

        @JsonProperty("refreshUrl")
        private String refreshUrl;

        private Map<String, String> scopes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PasswordOAuthFlow implements Serializable {

        private static final long serialVersionUID = 1L;

        @JsonProperty("tokenUrl")
        private String tokenUrl;

        @JsonProperty("refreshUrl")
        private String refreshUrl;

        private Map<String, String> scopes;
    }
}
