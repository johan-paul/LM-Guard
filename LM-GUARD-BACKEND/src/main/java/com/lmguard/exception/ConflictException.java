package com.lmguard.exception;

/** The request conflicts with existing state. Maps to HTTP 409. */
public class ConflictException extends ApiException {

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
