package com.starkgroup.vms.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, Object id) {
        super(resourceName + " with id " + id + " was not found");
    }
}
