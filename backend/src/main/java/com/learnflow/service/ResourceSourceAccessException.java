package com.learnflow.service;

import org.springframework.http.HttpStatus;

public class ResourceSourceAccessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ResourceSourceAccessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
