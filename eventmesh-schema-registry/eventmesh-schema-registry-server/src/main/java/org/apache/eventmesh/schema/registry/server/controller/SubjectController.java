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

package org.apache.eventmesh.schema.registry.server.controller;

import org.apache.eventmesh.schema.registry.server.domain.Schema;
import org.apache.eventmesh.schema.registry.server.domain.Subject;
import org.apache.eventmesh.schema.registry.server.domain.SubjectWithSchema;
import org.apache.eventmesh.schema.registry.server.response.SchemaIdResponse;
import org.apache.eventmesh.schema.registry.server.service.SubjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/subjects")
public class SubjectController {

    @Autowired
    SubjectService subjectService;

    @GetMapping({"","/"})
    public ResponseEntity<List<String>> getAllSubjects(){
        List<String> subjects = subjectService.getAllSubjects();
        return ResponseEntity.ok(subjects);
    }

    @GetMapping("/{subject}/versions")
    public ResponseEntity<List<Integer>> getAllVersionBySubject(@PathVariable("subject") String subject){
        List<Integer> versions = subjectService.getAllVersionsBySubject(subject);
        return ResponseEntity.ok(versions);
    }

    @DeleteMapping("/subjects/{subject}")
    public ResponseEntity<List<Integer>> deleteSubjectAndAllSchemaBySubject(@PathVariable("subject") String subject){
        List<Integer> versions = subjectService.deleteSubjectAndAllSchemaBySubject(subject);
        return ResponseEntity.ok(versions);
    }

    @GetMapping("/{subject}")
    public ResponseEntity<Subject> getSubjectByName(@PathVariable("subject") String subject){
        Subject getSubject = subjectService.getSubjectByName(subject);
        return ResponseEntity.ok(getSubject);
    }

    @GetMapping("/{subject}/versions/{version}/schema")
    public ResponseEntity<SubjectWithSchema> getSchemaBySubjectAndVersion(@PathVariable("subject")String subject, @PathVariable("version")int version){
        SubjectWithSchema subjectWithSchema = subjectService.getSchemaBySubjectAndVersion(subject, version);
        return ResponseEntity.ok(subjectWithSchema);
    }

    @PostMapping("/subjects/{subject}/versions")
    public ResponseEntity<SchemaIdResponse> checkOrRegisterSchema(@PathVariable("subject") String subject, @RequestBody Schema schema){
        SchemaIdResponse schemaIdResponse = subjectService.checkOrRegisterSchema(subject, schema);
        return ResponseEntity.ok(schemaIdResponse);
    }

    @PostMapping("/subjects/{subject}")
    public ResponseEntity<Subject> updateSubjectIfDifferent(@PathVariable("subject") String subjectName, @RequestBody Subject subject){
        Subject updatedSubject = subjectService.updateSubjectIfDifferent(subjectName, subject);
        return ResponseEntity.ok(updatedSubject);
    }

    @DeleteMapping("/subjects/{subject}/versions/{version}")
    public ResponseEntity<Integer> deleteSchemaBySubjectAndVersion(@PathVariable("subject")String subject, @PathVariable("version") int version){
        Integer deletedId = subjectService.deleteSchemaBySubjectAndVersion(subject, version);
        return ResponseEntity.ok(deletedId);
    }
}
