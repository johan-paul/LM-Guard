package com.lmguard.exception;

/** A requested entity does not exist. Maps to HTTP 404. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static ResourceNotFoundException of(ErrorCode errorCode, Object id) {
        return new ResourceNotFoundException(errorCode, errorCode.getDefaultMessage() + ": " + id);
    }
}
