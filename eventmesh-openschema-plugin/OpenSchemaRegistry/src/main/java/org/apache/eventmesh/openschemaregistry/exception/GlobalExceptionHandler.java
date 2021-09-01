package org.apache.eventmesh.openschemaregistry.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = OpenSchemaException.class)
    public ResponseEntity<ErrorResponse> OpenSchemaExceptionHandler(OpenSchemaException e){
        return ResponseEntity.status(e.getErr_status().value()).body(new ErrorResponse(e.getErr_code(), e.getErr_message()));
    }
}
