package org.apache.eventmesh.openschemaregistry.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ErrorResponse {
    private String err_code;
    private String err_message;
}
