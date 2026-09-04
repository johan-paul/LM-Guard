package com.lmguard.exception;

import lombok.Getter;

/**
 * Base class for every error this application raises deliberately.
 *
 * <p>Carrying the {@link ErrorCode} on the exception means the HTTP status and the client-facing
 * code are decided where the problem is detected, not guessed at in the handler.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
