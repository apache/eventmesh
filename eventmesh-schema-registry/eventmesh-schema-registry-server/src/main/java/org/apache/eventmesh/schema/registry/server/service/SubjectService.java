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

import org.apache.eventmesh.schema.registry.server.domain.*;
import org.apache.eventmesh.schema.registry.server.exception.ExceptionEnum;
import org.apache.eventmesh.schema.registry.server.exception.OpenSchemaException;
import org.apache.eventmesh.schema.registry.server.repository.SchemaRepository;
import org.apache.eventmesh.schema.registry.server.repository.SubjectRepository;
import org.apache.eventmesh.schema.registry.server.response.SchemaIdResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SubjectService {

    @Autowired
    SubjectRepository subjectRepository;

    @Autowired
    SchemaRepository schemaRepository;

    public List<String> getAllSubjects() {
        List<String> subjects;
        try {
            subjects = subjectRepository.getAllSubject();
        } catch (Exception e) {
            throw new OpenSchemaException(ExceptionEnum.StorageServiceException);
        }
        return subjects;
    }

    public List<Integer> getAllVersionsBySubject(String subject) {
        List<Integer> versions;
        try {
            versions = subjectRepository.getAllVersionsBySubject(subject);
        } catch (Exception e) {
            throw new OpenSchemaException(ExceptionEnum.StorageServiceException);
        }
        if (versions == null) {
            throw new OpenSchemaException(ExceptionEnum.SubjectNonExist);
        }
        return versions;
    }

    public List<Integer> deleteSubjectAndAllSchemaBySubject(String subject) {
        List<Integer> versions;
        Subject deletedSubject;
        try {
            deletedSubject = subjectRepository.deleteSubjectBySubject(subject);
        } catch (Exception e) {
            throw new OpenSchemaException(ExceptionEnum.StorageServiceException);
        }
        if (deletedSubject == null) {
            throw new OpenSchemaException(ExceptionEnum.SubjectNonExist);
        }

        try {
            versions = schemaRepository.deleteSchemasBySubject(subject);
        } catch (Exception e) {
            throw new OpenSchemaException(ExceptionEnum.StorageServiceException);
        }
        return versions;
    }

    public Subject getSubjectByName(String subject) {
        Subject getSubject;
        try {
            getSubject = subjectRepository.getSubjectBySubject(subject);
        } catch (Exception e) {
            throw new OpenSchemaException(ExceptionEnum.StorageServiceException);
        }

        if (getSubject == null) {
            throw new OpenSchemaException(ExceptionEnum.SubjectNonExist);
        }

        return getSubject;
    }

    public SubjectWithSchema getSchemaBySubjectAndVersion(String subject, int version) {
        Schema schema = null;
        Subject getSubject = null;
        try {
            getSubject = subjectRepository.getSubjectBySubject(subject);
            schema = schemaRepository.getSchemaBySubjectAndVersion(subject, version);
        } catch (Exception e) {
            throw new OpenSchemaException(ExceptionEnum.StorageServiceException);
        }

        if (getSubject == null) {
            throw new OpenSchemaException(ExceptionEnum.VersionNonExist);
        }

        if (schema == null) {
            throw new OpenSchemaException(ExceptionEnum.SubjectNonExist);
        }

        return new SubjectWithSchema(getSubject, schema);
    }


    public SchemaIdResponse checkOrRegisterSchema(String subject, Schema schema) {
        Subject subjectBySubject = null;
        try {
            subjectBySubject = subjectRepository.getSubjectBySubject(subject);
        } catch (Exception e) {
            throw new OpenSchemaException(ExceptionEnum.StorageServiceException);
        }

        if (subjectBySubject == null) {
            throw new OpenSchemaException(ExceptionEnum.SubjectNonExist);
        }

        List<SchemaWithSubjectName> schemaWithNames = null;
        try {
            schemaWithNames = schemaRepository.getSchemaWithSubjectNamesBySubjectOrderByVersionDesc(subject);
        } catch (Exception e) {
            throw new OpenSchemaException(ExceptionEnum.StorageServiceException);
        }

        SchemaIdResponse schemaIdResponse = schemaWithNames
                .stream()
                .filter(schemaWithSubjectName
                        -> schema.getSerialization().equals(schemaWithSubjectName.getSerialization())
                        && schema.getSchemaType().equals(schemaWithSubjectName.getSchemaType())
                        && schema.getSchemaDefinition().equals(schemaWithSubjectName.getSchemaType()))
                .findFirst()
                .map(schemaWithSubjectName
                        -> new SchemaIdResponse(String.valueOf(schemaWithSubjectName.getId())))
                .orElse(null);

        if (schemaIdResponse == null) {
            SchemaWithSubjectName schemaWithSubjectName = SchemaWithSubjectName
                    .builder()
                    .name(schema.getName())
                    .comment(schema.getComment())
                    .serialization(schema.getSerialization())
                    .schemaType(schema.getSchemaType())
                    .schemaDefinition(schema.getSchemaDefinition())
                    .validator(schema.getValidator())
                    .version(schemaWithNames.get(0).getVersion() + 1)
                    .subject(subject)
                    .build();
            try {
                SchemaWithSubjectName save = schemaRepository.save(schemaWithSubjectName);
                schemaIdResponse = new SchemaIdResponse(String.valueOf(save.getId()));
            } catch (Exception e) {
                throw new OpenSchemaException(ExceptionEnum.StorageServiceException);
            }

        }
        return schemaIdResponse;
    }

    public Subject updateSubjectIfDifferent(String subjectName, Subject subject) {
        Subject getSubject = null;
        Subject returnSubject = null;
        try {
            getSubject = subjectRepository.getSubjectBySubject(subjectName);
        } catch (Exception e) {
            throw new OpenSchemaException(ExceptionEnum.StorageServiceException);
        }
        if (getSubject == null) {
            returnSubject = subjectRepository.save(subject);
        } else {
            if (getSubject.equals(subject)) {
                returnSubject = subject;
            } else {
                //todo
                // if compatibility is changed, the left schemas should satisfy compatibility
            }
        }
        return returnSubject;
    }

    public Integer deleteSchemaBySubjectAndVersion(String subject, int version) {
        Integer deletedVersion = null;
        // todo complete the logic here for compatibility check
        try {
            deletedVersion = schemaRepository.deleteSchemaBySubjectAndVersion(subject, version);
        } catch (Exception e) {
            throw new OpenSchemaException(ExceptionEnum.StorageServiceException);
        }
        if (deletedVersion == null) {
            throw new OpenSchemaException(ExceptionEnum.SchemaNonExist);
        }
        return deletedVersion;
    }

    public Compatibility getCompatibilityBySubject(String subject) {
        Subject getSubject = null;
        try {
            getSubject = subjectRepository.getSubjectBySubject(subject);
        } catch (Exception e) {
            throw new OpenSchemaException(ExceptionEnum.StorageServiceException);
        }

        if (getSubject == null) {
            throw new OpenSchemaException(ExceptionEnum.SubjectNonExist);
        }

        return new Compatibility(getSubject.getCompatibility());
    }
}
