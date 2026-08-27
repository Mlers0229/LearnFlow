package com.learnflow.service;

public class ResourceDeletionException extends RuntimeException {

    private final String code;

    public ResourceDeletionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
