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

package org.apache.eventmesh.store.api.openschema.service;

import java.util.List;

import org.apache.eventmesh.spi.EventMeshSPI;
import org.apache.eventmesh.store.api.openschema.common.ServiceException;
import org.apache.eventmesh.store.api.openschema.request.CompatibilityCheckRequest;
import org.apache.eventmesh.store.api.openschema.request.CompatibilityRequest;
import org.apache.eventmesh.store.api.openschema.request.SchemaCreateRequest;
import org.apache.eventmesh.store.api.openschema.request.SubjectCreateRequest;
import org.apache.eventmesh.store.api.openschema.response.*;

@EventMeshSPI
public interface SchemaService {
    // Subject
    SubjectResponse createSubject(SubjectCreateRequest subjectCreateRequest) throws ServiceException;

    List<String> fetchAllSubjects() throws ServiceException;

    SubjectResponse fetchSubjectByName(String subject) throws ServiceException;

    SubjectResponse fetchSubjectBySchemaId(String schemaId) throws ServiceException;

    List<Integer> deleteSubject(String subject) throws ServiceException;

    // Schema
    SchemaResponse createSchema(SchemaCreateRequest schemaCreateRequest, String subject) throws ServiceException;

    SchemaResponse fetchSchemaById(String schemaId) throws ServiceException;

    SubjectResponse fetchSchemaBySubjectAndVersion(String subject, String version) throws ServiceException;

    List<Integer> fetchAllVersions(String subject) throws ServiceException;

    Integer deleteSchemaBySubjectAndVersion(String subject, String version) throws ServiceException;

    List<SubjectVersionResponse> fetchAllVersionsById(String schemaId) throws ServiceException;

    // Compatibility
    CompatibilityCheckResponse isSchemaCompatibleWithVersionInSubject(String subject, int version, CompatibilityCheckRequest compatibilityCheckRequest) throws ServiceException;

    CompatibilityResponse alterCompatibilityBySubject(String subject, CompatibilityRequest compatibilityRequest) throws ServiceException;

    CompatibilityResponse fetchCompatibilityBySubject(String subject) throws ServiceException;

}
