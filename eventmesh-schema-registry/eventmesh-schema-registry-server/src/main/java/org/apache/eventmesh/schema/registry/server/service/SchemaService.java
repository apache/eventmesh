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
package org.apache.eventmesh.schema.registry.server.service;

import org.apache.eventmesh.schema.registry.server.domain.Schema;
import org.apache.eventmesh.schema.registry.server.domain.SchemaWithSubjectName;
import org.apache.eventmesh.schema.registry.server.exception.ExceptionEnum;
import org.apache.eventmesh.schema.registry.server.exception.OpenSchemaException;
import org.apache.eventmesh.schema.registry.server.repository.SchemaRepository;
import org.apache.eventmesh.schema.registry.server.response.SubjectAndVersionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SchemaService {

    @Autowired
    SchemaRepository schemaRepository;

    public Schema getSchemaById(long id){
        SchemaWithSubjectName schemaWithSubjectName = null;
        try {
            schemaWithSubjectName = schemaRepository.getSchemaById(id);
        }catch (Exception e){
            throw new OpenSchemaException(ExceptionEnum.StorageServiceException);
        }
        if(schemaWithSubjectName==null){
            throw new OpenSchemaException(ExceptionEnum.SchemaNonExist);
        }
        Schema schema = new Schema();
        schema.setId(String.valueOf(schemaWithSubjectName.getId()));
        schema.setName(schemaWithSubjectName.getName());
        schema.setComment(schemaWithSubjectName.getComment());
        schema.setSerialization(schemaWithSubjectName.getSerialization());
        schema.setSchemaType(schemaWithSubjectName.getSchemaType());
        schema.setSchemaDefinition(schemaWithSubjectName.getSchemaDefinition());
        schema.setValidator(schemaWithSubjectName.getValidator());
        schema.setVersion(schemaWithSubjectName.getVersion());
        return schema;
    }

    public SubjectAndVersionResponse getSubjectAndVersionById(long id){
        SchemaWithSubjectName schemaWithSubjectName = null;
        try {
            schemaWithSubjectName = schemaRepository.getSchemaById(id);
        }catch (Exception e){
            throw new OpenSchemaException(ExceptionEnum.StorageServiceException);
        }
        if(schemaWithSubjectName==null){
            throw new OpenSchemaException(ExceptionEnum.SchemaNonExist);
        }
        return new SubjectAndVersionResponse(schemaWithSubjectName.getSubject(), schemaWithSubjectName.getVersion());
    }
}
