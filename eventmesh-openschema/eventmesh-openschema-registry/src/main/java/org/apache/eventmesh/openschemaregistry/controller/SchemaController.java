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
package org.apache.eventmesh.openschemaregistry.controller;

import org.apache.eventmesh.openschemaregistry.domain.Schema;
import org.apache.eventmesh.openschemaregistry.response.SubjectAndVersionResponse;
import org.apache.eventmesh.openschemaregistry.service.SchemaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/schemas")
public class SchemaController {
    @Autowired
    SchemaService schemaService;

    @GetMapping("/ids/{id}")
    public ResponseEntity<Schema> fetchSchemaById(@PathVariable("id") long id){
        Schema schema = schemaService.getSchemaById(id);
        return ResponseEntity.ok(schema);
    }

    @GetMapping("/ids/{id}/subjects")
    public ResponseEntity<SubjectAndVersionResponse> fetchSubjectAndVersionById(@PathVariable("id") long id){
        SubjectAndVersionResponse subjectAndVersionResponse = schemaService.getSubjectAndVersionById(id);
        return ResponseEntity.ok(subjectAndVersionResponse);
    }
}
