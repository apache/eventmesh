package org.apache.eventmesh.store.api.openschema.service;

import java.util.List;

import org.apache.eventmesh.store.api.openschema.common.ServiceException;
import org.apache.eventmesh.store.api.openschema.request.ConfigUpdateRequest;
import org.apache.eventmesh.store.api.openschema.request.SchemaCreateRequest;
import org.apache.eventmesh.store.api.openschema.request.SubjectCreateRequest;
import org.apache.eventmesh.store.api.openschema.response.CompatibilityCheckResponse;
import org.apache.eventmesh.store.api.openschema.response.CompatibilityResponse;
import org.apache.eventmesh.store.api.openschema.response.SchemaResponse;
import org.apache.eventmesh.store.api.openschema.response.SubjectResponse;
import org.apache.eventmesh.store.api.openschema.response.SubjectVersionResponse;

public interface SchemaService {

    SubjectResponse createSubject(SubjectCreateRequest subjectCreateRequest) throws ServiceException;
    
    SchemaResponse createSchema(SchemaCreateRequest schemaCreateRequest, String subject) throws ServiceException;

    SchemaResponse fetchSchemaById(String schemaId) throws ServiceException;
    
    SubjectResponse fetchSubjectByName(String subject) throws ServiceException;
    
    SubjectResponse fetchSchemaBySubjectAndVersion(String subject, String version) throws ServiceException;
    
    List<SubjectVersionResponse> fetchAllVersionsById(String schemaId) throws ServiceException;

    List<String> fetchAllSubjects() throws ServiceException;
    
    List<Integer> fetchAllVersions() throws ServiceException;
    
    List<Integer> deleteSubject(String subject) throws ServiceException;
    
    List<Integer> deleteSchemaBySubjectAndVersion(String subject, String version) throws ServiceException;
    
    CompatibilityCheckResponse checkCompatibilityBySubjectAndVersion(String subject, String version) throws ServiceException;
    
    CompatibilityResponse updateCompatibilityBySubject(String subject, ConfigUpdateRequest configUpdateRequest) throws ServiceException;
}
