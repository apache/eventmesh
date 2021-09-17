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
package org.apache.eventmesh.openschemaregistry.service;

import org.apache.eventmesh.openschemaregistry.domain.Compatibility;
import org.apache.eventmesh.openschemaregistry.domain.Schema;
import org.apache.eventmesh.openschemaregistry.domain.Subject;
import org.apache.eventmesh.openschemaregistry.exception.ExceptionEnum;
import org.apache.eventmesh.openschemaregistry.exception.OpenSchemaException;
import org.apache.eventmesh.openschemaregistry.repository.SchemaRepository;
import org.apache.eventmesh.openschemaregistry.repository.SubjectRepository;
import org.apache.eventmesh.openschemaregistry.response.CompatibilityResultResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CompatibilityService {

    @Autowired
    SubjectRepository subjectRepository;

    @Autowired
    SchemaRepository schemaRepository;


    public CompatibilityResultResponse checkWhetherCompatible(String subject, Integer version, Schema schema){
        Subject getSubject = null;
        Schema getSchema = null;
        CompatibilityResultResponse response = null;
        try {
            getSubject = subjectRepository.getSubjectBySubject(subject);
        }catch (Exception e){
            throw new OpenSchemaException(ExceptionEnum.StorageServiceException);
        }
        if(getSubject == null){
            throw new OpenSchemaException(ExceptionEnum.SubjectNonExist);
        }

        try {
            getSchema = schemaRepository.getSchemaBySubjectAndVersion(subject, version);
        }catch (Exception e){
            throw new OpenSchemaException(ExceptionEnum.StorageServiceException);
        }
        if(getSchema == null){
            throw new OpenSchemaException(ExceptionEnum.SchemaNonExist);
        }
        // todo check the compatibility

        return response;
    }


    public Compatibility getCompatibilityBySubject(String subject){
        Subject getSubject = null;
        try {
            getSubject = subjectRepository.getSubjectBySubject(subject);
        }catch (Exception e){
            throw new OpenSchemaException(ExceptionEnum.StorageServiceException);
        }

        if(getSubject==null){
            throw new OpenSchemaException(ExceptionEnum.SubjectNonExist);
        }

        return new Compatibility(getSubject.getCompatibility());
    }

    public Compatibility updateCompatibilityBySubject(String subject, Compatibility compatibility){
        Subject getSubject = null;
        try {
            getSubject = subjectRepository.getSubjectBySubject(subject);
        }catch (Exception e){
            throw new OpenSchemaException(ExceptionEnum.StorageServiceException);
        }
        if(getSubject == null){
            throw new OpenSchemaException(ExceptionEnum.SubjectNonExist);
        }
        getSubject.setCompatibility(compatibility.getCompatibility());
        try {
            // todo check the compatibility among schemas of newly set compatibility
            subjectRepository.save(getSubject);
        }catch (Exception e){
            throw new OpenSchemaException(ExceptionEnum.StorageServiceException);
        }
        return compatibility;
    }
}
