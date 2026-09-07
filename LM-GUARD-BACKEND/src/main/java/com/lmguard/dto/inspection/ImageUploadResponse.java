package com.lmguard.dto.inspection;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "ImageUploadResponse", description = "Result of storing a package image")
public record ImageUploadResponse(

        UUID inspectionId,

        @Schema(description = "Publicly resolvable URL of the stored image")
        String imageUrl,

        @Schema(description = "Bucket-relative storage path")
        String imagePath,

        @Schema(description = "Stored size in bytes", example = "284913")
        long sizeBytes,

        @Schema(description = "Detected content type", example = "image/jpeg")
        String contentType
) {
}
