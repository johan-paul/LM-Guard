package com.lmguard.dto.inspection;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "BoundingBox", description = "Pixel region on the stored package image")
public record BoundingBoxResponse(
        @Schema(example = "120") Integer x,
        @Schema(example = "340") Integer y,
        @Schema(example = "200") Integer width,
        @Schema(example = "80") Integer height
) {

    public static BoundingBoxResponse of(Integer x, Integer y, Integer width, Integer height) {
        if (x == null || y == null || width == null || height == null) {
            return null;
        }
        return new BoundingBoxResponse(x, y, width, height);
    }
}
