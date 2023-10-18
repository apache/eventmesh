package org.apache.eventmesh.admin.exception;

import org.apache.eventmesh.admin.dto.Result;

import javax.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public Result<Object> baseHandler(BaseException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("RESTful API {} service error occurred: ", requestURI, e);
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<Object> runtimeHandler(RuntimeException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("RESTful API {} runtime error occurred: ", requestURI, e);
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Object> exceptionHandler(Exception e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("RESTful API {} unknown error occurred: ", requestURI, e);
        return Result.error(e.getMessage());
    }

}
