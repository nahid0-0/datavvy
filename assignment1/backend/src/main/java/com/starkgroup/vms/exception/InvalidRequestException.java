package com.starkgroup.vms.exception;

/** A semantically invalid request that bean validation cannot express (e.g. an unsupported sort field). */
public class InvalidRequestException extends RuntimeException {

    private final String field;

    public InvalidRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
