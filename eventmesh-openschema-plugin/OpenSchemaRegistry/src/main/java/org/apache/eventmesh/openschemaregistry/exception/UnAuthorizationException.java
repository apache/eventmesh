package org.apache.eventmesh.openschemaregistry.exception;


import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

public class UnAuthorizationException extends OpenSchemaException{
    public UnAuthorizationException(HttpStatus err_status, String err_code, String err_message) {
        super(err_status, err_code, err_message);
    }
}
