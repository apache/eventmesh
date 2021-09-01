package org.apache.eventmesh.openschemaregistry.controller;

import org.apache.eventmesh.openschemaregistry.response.SchemaResponse;
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
    public ResponseEntity<SchemaResponse> fetchSchemaById(@PathVariable("id") long id){
        SchemaResponse schemaResponse = schemaService.getSchemaById(id);
        return ResponseEntity.ok(schemaResponse);
    }

    @GetMapping("/ids/{id}/subjects")
    public ResponseEntity<SubjectAndVersionResponse> fetchSubjectAndVersionById(@PathVariable("id") long id){
        SubjectAndVersionResponse subjectAndVersionResponse = schemaService.getSubjectAndVersionById(id);
        return ResponseEntity.ok(subjectAndVersionResponse);
    }
}
