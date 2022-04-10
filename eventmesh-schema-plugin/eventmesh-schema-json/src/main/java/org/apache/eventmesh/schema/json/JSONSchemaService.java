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

package org.apache.eventmesh.schema.json;

import org.apache.eventmesh.common.schema.SchemaAgenda;
import org.apache.eventmesh.schema.api.SchemaService;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;

public class JSONSchemaService implements SchemaService {
    @Override
    public void contract() {
        //TODO
    }

    @Override
    public void serialize() {
        //TODO
    }

    @Override
    public void deserialize() {
        //TODO
    }

    /**
     * Check the validity of a json string according to json schema.
     * @param schemaAgenda a wrapper of content, content type, and schema
     * @return whether the json string is valid or not
     */
    @Override
    public boolean checkSchemaValidity(SchemaAgenda schemaAgenda) {
        boolean validity = false;
        try {
            JsonNode contentJson = JsonLoader.fromString(schemaAgenda.getContent());
            JsonNode schemaJson = JsonLoader.fromString(schemaAgenda.getSchema().getSchemaDefinition());
            JsonSchemaFactory schemaFactory = JsonSchemaFactory.byDefault();
            JsonSchema schema = schemaFactory.getJsonSchema(schemaJson);
            ProcessingReport report = schema.validate(contentJson);
            validity = report.isSuccess();
        } catch (IOException | ProcessingException e) {
            e.printStackTrace();
        }
        return validity;
    }
}
