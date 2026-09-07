package com.lmguard.exception;

/** A file could not be stored, read or deleted. */
public class StorageException extends ApiException {

    public StorageException(String message) {
        super(ErrorCode.STORAGE_ERROR, message);
    }

    public StorageException(String message, Throwable cause) {
        super(ErrorCode.STORAGE_ERROR, message, cause);
    }
}
