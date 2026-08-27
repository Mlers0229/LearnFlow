package com.learnflow.service;

public class ResourceActivationException extends RuntimeException {

    private final String code;

    public ResourceActivationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
