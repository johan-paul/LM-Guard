package com.lmguard.exception;

import org.springframework.http.HttpStatus;

/**
 * Every error the API can return, with its stable string code and HTTP status.
 *
 * <p>Clients branch on the code, never on the message text. Messages may be reworded;
 * codes are part of the contract.
 */
public enum ErrorCode {

    // --- 400 ---
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "Uploaded file is not a supported image"),
    IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "An image must be uploaded before analysis"),
    INSPECTION_NOT_ANALYSABLE(HttpStatus.BAD_REQUEST, "Inspection is not in a state that can be analysed"),

    // --- 401 ---
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email or password is incorrect"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Authentication token has expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Authentication token is invalid"),

    // --- 403 ---
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "This account has been disabled"),

    // --- 404 ---
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Product not found"),
    INSPECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Inspection not found"),
    VIOLATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Violation not found"),
    EVIDENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Evidence not found"),
    RULE_NOT_FOUND(HttpStatus.NOT_FOUND, "Rule not found"),
    RULESET_NOT_FOUND(HttpStatus.NOT_FOUND, "No active rules found for the requested ruleset version"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),

    // --- 409 ---
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "An account with this email already exists"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Resource already exists"),

    // --- 413 / 415 ---
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the maximum allowed size"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type"),

    // --- 5xx ---
    STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "File storage operation failed"),
    RULE_ENGINE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Rule engine could not evaluate this inspection"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error"),

    // --- 502 ---
    AI_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "AI analysis service is unavailable or returned an error");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
