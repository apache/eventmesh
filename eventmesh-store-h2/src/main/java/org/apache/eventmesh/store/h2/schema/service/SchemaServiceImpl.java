package org.apache.eventmesh.store.h2.schema.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.eventmesh.store.api.openschema.common.ServiceError;
import org.apache.eventmesh.store.api.openschema.common.ServiceException;
import org.apache.eventmesh.store.api.openschema.request.ConfigUpdateRequest;
import org.apache.eventmesh.store.api.openschema.request.SchemaCreateRequest;
import org.apache.eventmesh.store.api.openschema.request.SubjectCreateRequest;
import org.apache.eventmesh.store.api.openschema.response.CompatibilityCheckResponse;
import org.apache.eventmesh.store.api.openschema.response.CompatibilityResponse;
import org.apache.eventmesh.store.api.openschema.response.SchemaResponse;
import org.apache.eventmesh.store.api.openschema.response.SubjectResponse;
import org.apache.eventmesh.store.api.openschema.response.SubjectVersionResponse;
import org.apache.eventmesh.store.api.openschema.service.SchemaService;
import org.apache.eventmesh.store.h2.schema.H2SchemaAdapter;
import org.apache.eventmesh.store.h2.schema.domain.Schema;
import org.apache.eventmesh.store.h2.schema.domain.Subject;
import org.apache.eventmesh.store.h2.schema.dto.converter.SchemaConverter;
import org.apache.eventmesh.store.h2.schema.dto.converter.SubjectConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaServiceImpl implements SchemaService {
	
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private SubjectConverter subjectConverter;
	
	private SchemaConverter schemaConverter;
	
	@Override
	public SubjectResponse createSubject(SubjectCreateRequest subjectCreateRequest) throws ServiceException {		
		try {
			Subject subject = H2SchemaAdapter.getSubjectRepository().insertSubject(subjectCreateRequest);
			subjectConverter = new SubjectConverter();
			return subjectConverter.toSubjectResponse(subject);
		} catch (SQLException e) {
			logger.error("failed to create subject", e);
	        throw new ServiceException(ServiceError.ERR_SERVER_ERROR);
		}				
	}

	@Override
	public SchemaResponse createSchema(SchemaCreateRequest schemaCreateRequest, String subject) throws ServiceException {
		try {
			String id = H2SchemaAdapter.getSchemaRepository().insertSchema(schemaCreateRequest, subject);
			SchemaResponse schemaResponse = new SchemaResponse(null, null, null, null, null, null, null);
			schemaResponse.setId(id);
			return schemaResponse;
		} catch (SQLException e) {
			logger.error("failed to create schema", e);
	        throw new ServiceException(ServiceError.ERR_SERVER_ERROR);
		}
	}

	@Override
	public SubjectResponse fetchSubjectByName(String name) throws ServiceException {
		try {
			Subject subject = H2SchemaAdapter.getSubjectRepository().getSubjectByName(name);
			subjectConverter = new SubjectConverter();
			return subjectConverter.toSubjectResponse(subject);
		} catch (SQLException e) {
			logger.error("failed to fetch subject by name", e);
	        throw new ServiceException(ServiceError.ERR_SERVER_ERROR);
		}
	}
	
	@Override
	public List<String> fetchAllSubjects() throws ServiceException{
		try {
			List<String> allSubjects = H2SchemaAdapter.getSubjectRepository().getAllSubjects();			
			return allSubjects;
		} catch (SQLException e) {
			logger.error("failed to fetch all subjects", e);
	        throw new ServiceException(ServiceError.ERR_SERVER_ERROR);
		}
	}
	
	@Override
	public SchemaResponse fetchSchemaById(String schemaId) throws ServiceException{
		try {
			Schema schema = H2SchemaAdapter.getSchemaRepository().getSchemaById(schemaId);		
			schemaConverter = new SchemaConverter();
			return schemaConverter.toSchemaResponse(schema);
		} catch (SQLException e) {
			logger.error("failed to fetch schema by id", e);
	        throw new ServiceException(ServiceError.ERR_SERVER_ERROR);
		}
	}

	@Override
	public SubjectResponse fetchSchemaBySubjectAndVersion(String subject, String version) throws ServiceException{		
		try {
			int integerVersion = Integer.valueOf(version);
			Schema schemaDomain = H2SchemaAdapter.getSchemaRepository().getSchemaBySubjectAndVersion(subject, integerVersion);		
			schemaConverter = new SchemaConverter();
			SchemaResponse schemaResponse = schemaConverter.toSchemaResponse(schemaDomain);
			
			Subject subjectDomain = H2SchemaAdapter.getSubjectRepository().getSubjectByName(subject);
			subjectConverter = new SubjectConverter();
			return subjectConverter.toSubjectAndSchemaResponse(subjectDomain, schemaResponse);
		} catch (SQLException e) {
			logger.error("failed to fetch schema by subject and version", e);
	        throw new ServiceException(ServiceError.ERR_SERVER_ERROR);
		}
	}

	@Override
	public List<SubjectVersionResponse> fetchAllVersionsById(String schemaId) throws ServiceException{
		try {
			Schema schema = H2SchemaAdapter.getSchemaRepository().getSchemaById(schemaId);		
			schemaConverter = new SchemaConverter();
			List<SubjectVersionResponse> subjectVersionResponse = new ArrayList<SubjectVersionResponse>();
			subjectVersionResponse.add(schemaConverter.toSubjectVersionResponse(schema));
			return subjectVersionResponse;
		} catch (SQLException e) {
			logger.error("failed to fetch subject and version by schema id", e);
	        throw new ServiceException(ServiceError.ERR_SERVER_ERROR);
		}
	
	}	

	@Override
	public List<Integer> fetchAllVersions(String subject) throws ServiceException{
		try {
			List<Integer> allVersions = H2SchemaAdapter.getSchemaRepository().getAllVersionsBySubject(subject);		
			return allVersions;
		} catch (SQLException e) {
			logger.error("failed to fetch all versions by subject", e);
	        throw new ServiceException(ServiceError.ERR_SERVER_ERROR);
		}
	}

	@Override
	public List<Integer> deleteSubject(String subject) throws ServiceException {
		try {
			List<Integer> allVersions = H2SchemaAdapter.getSchemaRepository().getAllVersionsBySubject(subject);			
			int deletedRows = H2SchemaAdapter.getSchemaRepository().deleteAllSchemaVersionsBySubject(subject);
			if (deletedRows > 0) {
				return allVersions;
			}
			throw new ServiceException(ServiceError.ERR_SCHEMA_INVALID);
		} catch (SQLException e) {
			logger.error("failed to fetch schema by subject and version", e);
	        throw new ServiceException(ServiceError.ERR_SERVER_ERROR);
		}
	}

	@Override
	public Integer deleteSchemaBySubjectAndVersion(String subject, String version) throws ServiceException {		
		try {
			int integerVersion = Integer.valueOf(version);
			Schema schema = H2SchemaAdapter.getSchemaRepository().getSchemaBySubjectAndVersion(subject, integerVersion);
			int deletedRows = H2SchemaAdapter.getSchemaRepository().deleteSchemaBySubjectAndVersion(subject, integerVersion);
			if (deletedRows > 0) {
				return schema.getVersion();
			}
			throw new ServiceException(ServiceError.ERR_SUBJECT_INVALID);
		} catch (SQLException e) {
			logger.error("failed to fetch schema by subject and version", e);
	        throw new ServiceException(ServiceError.ERR_SERVER_ERROR);
		}
	}

	/*@Override
	public CompatibilityCheckResponse checkCompatibilityBySubjectAndVersion(String subject, String version) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompatibilityResponse updateCompatibilityBySubject(String subject, ConfigUpdateRequest configUpdateRequest) {
		// TODO Auto-generated method stub
		return null;
	}*/
		
}
