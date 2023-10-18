package org.apache.eventmesh.admin.exception;

import org.apache.eventmesh.admin.enums.Errors;
import org.apache.eventmesh.admin.utils.ExceptionUtils;

public class BaseException extends RuntimeException {

    private static final long serialVersionUID = 3509261993355721168L;

    public BaseException(String message) {
        super(message);
    }

    public BaseException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Customized error reporting with exception
     */
    public BaseException(Errors errors, Throwable cause) {
        super(ExceptionUtils.trimDesc(errors.getDesc()) + ": " + cause.getMessage(), cause);
    }

    public BaseException(Errors errors) {
        super(ExceptionUtils.trimDesc(errors.getDesc()));
    }
}
