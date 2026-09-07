package com.lmguard.mapper;

import com.lmguard.dto.product.ProductResponse;
import com.lmguard.dto.product.ProductVersionResponse;
import com.lmguard.entity.Product;
import com.lmguard.entity.ProductVersion;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        return toResponse(product, null);
    }

    public ProductResponse toResponse(Product product, String imageUrl) {
        if (product == null) {
            return null;
        }
        return new ProductResponse(
                product.getId(),
                product.getProductName(),
                product.getBrand(),
                product.getCategory(),
                product.getBarcode(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                imageUrl
        );
    }

    public ProductVersionResponse toResponse(ProductVersion version) {
        if (version == null) {
            return null;
        }
        return new ProductVersionResponse(
                version.getId(),
                version.getVersionNumber() == null ? 0 : version.getVersionNumber(),
                version.getMrp(),
                version.getNetQuantity(),
                version.getManufacturer(),
                version.getOrigin(),
                version.getConsumerCare(),
                version.getCapturedAt(),
                version.getSource()
        );
    }
}
