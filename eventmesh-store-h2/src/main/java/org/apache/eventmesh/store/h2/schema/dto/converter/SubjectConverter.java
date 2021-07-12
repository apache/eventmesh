package org.apache.eventmesh.store.h2.schema.dto.converter;

import org.apache.eventmesh.store.api.openschema.response.SubjectResponse;
import org.apache.eventmesh.store.h2.schema.domain.Subject;

public class SubjectConverter {

    //@Resource
    //private ConverterUtil converterUtil;
	public SubjectConverter() {}
	
    public SubjectResponse toSubjectResponse(Subject subject) {
    	SubjectResponse subjectResponse = new SubjectResponse(subject.getTenant(), subject.getNamespace(),
    			subject.getName(), subject.getApp(),
    			subject.getDescription(),subject.getStatus(),
    			subject.getCompatibility(),subject.getCoordinate(),null);
        return subjectResponse;
    }
}
