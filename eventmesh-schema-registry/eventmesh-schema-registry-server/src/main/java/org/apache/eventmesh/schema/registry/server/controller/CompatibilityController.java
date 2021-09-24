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

import org.apache.eventmesh.schema.registry.server.domain.Compatibility;
import org.apache.eventmesh.schema.registry.server.domain.Schema;
import org.apache.eventmesh.schema.registry.server.response.CompatibilityResultResponse;
import org.apache.eventmesh.schema.registry.server.service.CompatibilityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/compatibility")
public class CompatibilityController {

    @Autowired
    CompatibilityService compatibilityService;

    @PostMapping("/subjects/{subject}/versions/{version}")
    public ResponseEntity<CompatibilityResultResponse> checkWhetherCompatible(@PathVariable("subject") String subject,
                                                                              @PathVariable("version") Integer version,
                                                                              @RequestBody Schema schema){
        CompatibilityResultResponse resultResponse = compatibilityService.checkWhetherCompatible(subject, version, schema);
        return ResponseEntity.ok(resultResponse);
    }

    @GetMapping("/{subject}")
    public ResponseEntity<Compatibility> getCompatibilityBySubject(@PathVariable("subject") String subject){
        Compatibility compatibility = compatibilityService.getCompatibilityBySubject(subject);
        return ResponseEntity.ok(compatibility);
    }

    @PutMapping("/{subject}")
    public ResponseEntity<Compatibility> updateCompatibilityBySubject(@PathVariable("subject") String subject,
                                                                      @RequestBody Compatibility compatibility){
        Compatibility updatedCompatibility = compatibilityService.updateCompatibilityBySubject(subject, compatibility);
        return ResponseEntity.ok(updatedCompatibility);
    }
}
