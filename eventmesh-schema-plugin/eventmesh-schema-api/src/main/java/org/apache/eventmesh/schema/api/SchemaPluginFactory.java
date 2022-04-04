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

package org.apache.eventmesh.schema.api;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import lombok.experimental.UtilityClass;

/**
 * The plugin for schema.
 */
@UtilityClass
public class SchemaPluginFactory {

    /**
     * Get {@code SchemaService}
     *
     * @param schemaServiceType the name of schema service
     * @return schema service plugin or null
     */
    public static SchemaService getSchemaService(String schemaServiceType) {
        checkNotNull(schemaServiceType, "SchemaServiceType cannot be null");
        SchemaService schemaService = EventMeshExtensionFactory.getExtension(SchemaService.class, schemaServiceType);
        return checkNotNull(schemaService, "SchemaServiceType: " + schemaServiceType + " is not supported");
    }
}
