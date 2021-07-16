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

package org.apache.eventmesh.store.h2.schema.dto.converter;

import org.apache.eventmesh.store.api.openschema.response.SchemaResponse;
import org.apache.eventmesh.store.api.openschema.response.SubjectResponse;
import org.apache.eventmesh.store.h2.schema.domain.Subject;

public class SubjectConverter {

	public SubjectConverter() {}
	
    public SubjectResponse toSubjectResponse(Subject subject) {
    	SubjectResponse subjectResponse = new SubjectResponse(subject.getTenant(), subject.getNamespace(),
    			subject.getName(), subject.getApp(),
    			subject.getDescription(),subject.getStatus(),
    			subject.getCompatibility(),subject.getCoordinate(),null);
        return subjectResponse;
    }
    
    public SubjectResponse toSubjectAndSchemaResponse(Subject subject, SchemaResponse schemaResponse) {
    	SubjectResponse subjectResponse = new SubjectResponse(subject.getTenant(), subject.getNamespace(),
    			subject.getName(), subject.getApp(),
    			subject.getDescription(),subject.getStatus(),
    			subject.getCompatibility(),subject.getCoordinate(),schemaResponse);
        return subjectResponse;
    }
    
}
