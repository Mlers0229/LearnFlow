package com.learnflow.service;

public class ResourceIngestionException extends RuntimeException {
    private final String code;
    public ResourceIngestionException(String code, String message) { super(message); this.code = code; }
    public ResourceIngestionException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
    public String getCode() { return code; }
}
