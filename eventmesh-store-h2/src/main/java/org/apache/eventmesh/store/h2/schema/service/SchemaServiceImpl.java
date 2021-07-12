package org.apache.eventmesh.store.h2.schema.service;

import java.sql.SQLException;
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
import org.apache.eventmesh.store.h2.schema.domain.Subject;
import org.apache.eventmesh.store.h2.schema.dto.converter.SubjectConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaServiceImpl implements SchemaService {
	
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private SubjectConverter subjectConverter;
	
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
	public SchemaResponse createSchema(SchemaCreateRequest schemaCreateRequest, String subject) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SubjectResponse fetchSubjectByName(String name) throws ServiceException {
		try {
			Subject subject = H2SchemaAdapter.getSubjectRepository().getSubjectByName(name);
			subjectConverter = new SubjectConverter();
			return subjectConverter.toSubjectResponse(subject);
		} catch (SQLException e) {
			logger.error("failed to fetch subject", e);
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
	public SchemaResponse fetchSchemaById(String schemaId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SubjectResponse fetchSchemaBySubjectAndVersion(String subject, String version) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<SubjectVersionResponse> fetchAllVersionsById(String schemaId) {
		// TODO Auto-generated method stub
		return null;
	}	

	@Override
	public List<Integer> fetchAllVersions() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Integer> deleteSubject(String subject) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Integer> deleteSchemaBySubjectAndVersion(String subject, String version) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompatibilityCheckResponse checkCompatibilityBySubjectAndVersion(String subject, String version) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompatibilityResponse updateCompatibilityBySubject(String subject, ConfigUpdateRequest configUpdateRequest) {
		// TODO Auto-generated method stub
		return null;
	}
		
}
