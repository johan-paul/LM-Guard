package com.lmguard.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "ProductHistoryResponse",
        description = "A product with its full declaration history, newest version first")
public record ProductHistoryResponse(

        @Schema(description = "The product")
        ProductResponse product,

        @Schema(description = "Declaration snapshots, newest first")
        List<ProductVersionResponse> versions,

        @Schema(description = "Number of snapshots in which at least one declared value changed",
                example = "2")
        int changeCount
) {
}
