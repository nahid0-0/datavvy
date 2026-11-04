package com.starkgroup.vms.exception;

/** Machine-readable error codes returned in the {@code code} property of every error response. */
public enum ErrorCode {
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    RESOURCE_NOT_FOUND,
    DUPLICATE_VEHICLE_NUMBER,
    VERSION_CONFLICT,
    DATA_INTEGRITY_VIOLATION,
    METHOD_NOT_ALLOWED,
    UNSUPPORTED_MEDIA_TYPE,
    INTERNAL_ERROR
}
