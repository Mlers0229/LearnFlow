package com.learnflow.service;

import org.springframework.web.client.RestClientException;

public class AgentCallException extends RestClientException {

    public enum Reason {
        CONNECT_TIMEOUT,
        READ_TIMEOUT,
        OVERALL_TIMEOUT,
        CANCELLED,
        BULKHEAD_FULL,
        CIRCUIT_OPEN,
        HTTP_ERROR,
        SERIALIZATION_ERROR,
        IO_ERROR
    }

    private final AgentOperation operation;
    private final Reason reason;
    private final Integer statusCode;

    public AgentCallException(
            AgentOperation operation,
            Reason reason,
            String message,
            Throwable cause,
            Integer statusCode
    ) {
        super(message, cause);
        this.operation = operation;
        this.reason = reason;
        this.statusCode = statusCode;
    }

    public AgentOperation getOperation() {
        return operation;
    }

    public Reason getReason() {
        return reason;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
