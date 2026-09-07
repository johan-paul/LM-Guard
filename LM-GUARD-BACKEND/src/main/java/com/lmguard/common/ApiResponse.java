package com.lmguard.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * The single response envelope for every endpoint, success or failure.
 *
 * <p>A uniform shape means the React client writes its unwrapping and error handling once.
 * {@code data} is present on success, {@code errorCode} on failure; nulls are omitted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiResponse", description = "Uniform response envelope used by every LM-GUARD endpoint")
public record ApiResponse<T>(

        @Schema(description = "True when the request succeeded", example = "true")
        boolean success,

        @Schema(description = "Human-readable outcome message", example = "Inspection completed successfully")
        String message,

        @Schema(description = "Response payload; absent on failure")
        T data,

        @Schema(description = "Stable machine-readable error code; absent on success", example = "INSPECTION_NOT_FOUND")
        String errorCode,

        @Schema(description = "Server time the response was produced (UTC)", example = "2026-09-04T12:00:00Z")
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "OK", data, null, Instant.now());
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null, Instant.now());
    }

    public static ApiResponse<Void> message(String message) {
        return new ApiResponse<>(true, message, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return new ApiResponse<>(false, message, null, errorCode, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message, String errorCode, T data) {
        return new ApiResponse<>(false, message, data, errorCode, Instant.now());
    }
}
