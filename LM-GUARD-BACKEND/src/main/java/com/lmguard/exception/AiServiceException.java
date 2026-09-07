package com.lmguard.exception;

/** The external AI/OCR service failed or returned something unusable. */
public class AiServiceException extends ApiException {

    public AiServiceException(String message) {
        super(ErrorCode.AI_SERVICE_ERROR, message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(ErrorCode.AI_SERVICE_ERROR, message, cause);
    }
}
