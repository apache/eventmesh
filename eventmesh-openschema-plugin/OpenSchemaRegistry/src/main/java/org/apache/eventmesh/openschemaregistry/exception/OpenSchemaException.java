package org.apache.eventmesh.openschemaregistry.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

@Getter
@Setter
@AllArgsConstructor
public class OpenSchemaException extends RuntimeException{
    protected HttpStatus err_status;
    protected String err_code;
    protected String err_message;
}
