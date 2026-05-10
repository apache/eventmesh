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

package org.apache.eventmesh.protocol.a2a;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * Validates Agent Card JSON against the A2A Agent Card JSON Schema.
 */
@Slf4j
public class AgentCardValidator {

    private static final String SCHEMA_RESOURCE = "/a2a/agent_card_schema.json";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final JsonSchema schema;
    private final boolean schemaValidationEnabled;

    public AgentCardValidator(boolean schemaValidationEnabled) {
        this.schemaValidationEnabled = schemaValidationEnabled;
        this.schema = loadSchema();
    }

    public AgentCardValidator() {
        this(true);
    }

    private JsonSchema loadSchema() {
        try (InputStream is = AgentCardValidator.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (is == null) {
                log.warn("Agent card schema resource not found: {}, schema validation will be disabled", SCHEMA_RESOURCE);
                return null;
            }
            JsonNode schemaNode = objectMapper.readTree(is);
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V6);
            return factory.getSchema(schemaNode);
        } catch (IOException e) {
            log.warn("Failed to load agent card schema, validation will be disabled: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Validates an Agent Card JSON string.
     *
     * @param cardJson the card JSON string
     * @return ValidationResult with success/failure and error messages
     */
    public ValidationResult validate(String cardJson) {
        if (cardJson == null || cardJson.isEmpty()) {
            return ValidationResult.failure("Card JSON is null or empty");
        }

        if (!schemaValidationEnabled || schema == null) {
            // Only check it's valid JSON object
            try {
                JsonNode node = objectMapper.readTree(cardJson);
                if (!node.isObject()) {
                    return ValidationResult.failure("Card must be a JSON object");
                }
                return ValidationResult.success();
            } catch (Exception e) {
                return ValidationResult.failure("Card is not valid JSON: " + e.getMessage());
            }
        }

        try {
            JsonNode cardNode = objectMapper.readTree(cardJson);
            if (!cardNode.isObject()) {
                return ValidationResult.failure("Card must be a JSON object");
            }
            Set<ValidationMessage> messages = schema.validate(cardNode);
            if (messages.isEmpty()) {
                return ValidationResult.success();
            }
            StringBuilder sb = new StringBuilder("Card schema validation failed: ");
            for (ValidationMessage msg : messages) {
                sb.append(msg.getMessage()).append("; ");
            }
            return ValidationResult.failure(sb.toString());
        } catch (IOException e) {
            return ValidationResult.failure("Failed to parse card JSON: " + e.getMessage());
        }
    }

    /**
     * Validates that an ID segment matches the allowed pattern.
     */
    public static boolean validateId(String id) {
        return id != null && id.matches(A2AProtocolConstants.SEGMENT_ID_PATTERN);
    }

    public static class ValidationResult {

        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
