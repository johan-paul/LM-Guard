package com.lmguard.ai;

import com.lmguard.entity.Inspection;
import com.lmguard.entity.Product;
import com.lmguard.entity.enums.ProductField;
import com.lmguard.repository.InspectionRepository;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic stand-in for the Python AI service.
 *
 * <p>It dynamically extracts realistic facts matching the target inspection's officer-entered
 * product name, establishment/brand, or package photo (e.g. KitKat wrapper: MRP Rs 10.00, Net Qty 15.5 g).
 */
@Service
@Slf4j
@NoArgsConstructor
public class MockAIAnalysisService implements AIAnalysisService {

    private static final String MODEL_VERSION = "mock-1.0.0";

    private InspectionRepository inspectionRepository;

    @Autowired(required = false)
    public MockAIAnalysisService(InspectionRepository inspectionRepository) {
        this.inspectionRepository = inspectionRepository;
    }

    @Override
    public AIAnalysisResult analyzeImage(String imageUrl, UUID inspectionId) {
        long startedAt = System.currentTimeMillis();
        log.info("Mock AI analysing inspection {} (image: {})", inspectionId, imageUrl);

        int variant = variantFor(inspectionId);

        Inspection inspection = null;
        if (inspectionRepository != null && inspectionId != null) {
            inspection = inspectionRepository.findById(inspectionId).orElse(null);
        }

        Product product = inspection != null ? inspection.getProduct() : null;
        String productName = product != null && product.getProductName() != null ? product.getProductName() : null;
        String establishment = inspection != null ? inspection.getEstablishment() : null;
        String brand = product != null && product.getBrand() != null ? product.getBrand() : establishment;

        String combinedName = (productName != null ? productName : "") + " " + (establishment != null ? establishment : "") + " " + (imageUrl != null ? imageUrl : "");
        String lowerCombined = combinedName.toLowerCase();

        boolean isKitKat = lowerCombined.contains("kitkat") || lowerCombined.contains("kit kat") || lowerCombined.contains("nestle") || lowerCombined.contains("1788849683986");

        String mrpVal;
        String netQtyVal;
        String mfrVal;
        String commodityVal;

        if (isKitKat) {
            mrpVal = "10.00";
            netQtyVal = "15.5 g";
            mfrVal = (brand != null && !brand.isBlank()) ? brand : "NESTLE INDIA LTD.";
            commodityVal = (productName != null && !productName.isBlank()) ? productName : "KitKat 4 Finger Chocolate";
        } else if (lowerCombined.contains("chips") || lowerCombined.contains("snack")) {
            mrpVal = "20.00";
            netQtyVal = "50 g";
            mfrVal = (brand != null && !brand.isBlank()) ? brand : "ABC Foods";
            commodityVal = (productName != null && !productName.isBlank()) ? productName : "Classic Salted Chips";
        } else if (lowerCombined.contains("soap") || lowerCombined.contains("bath")) {
            mrpVal = "35.00";
            netQtyVal = "100 g";
            mfrVal = (brand != null && !brand.isBlank()) ? brand : "Bath & Hygiene Care";
            commodityVal = (productName != null && !productName.isBlank()) ? productName : "Bath Soap Bar";
        } else {
            mrpVal = "40.00";
            netQtyVal = "250 g";
            mfrVal = (brand != null && !brand.isBlank()) ? brand : "ABC Foods";
            commodityVal = (productName != null && !productName.isBlank()) ? productName :
                    switch (variant) {
                        case 0 -> "Packaged Commodity (" + (Math.abs(inspectionId != null ? inspectionId.hashCode() : 0) % 1000) + ")";
                        case 1 -> "Quality Consumer Goods (" + (Math.abs(inspectionId != null ? inspectionId.hashCode() : 0) % 1000) + ")";
                        default -> "Retail Packaged Product (" + (Math.abs(inspectionId != null ? inspectionId.hashCode() : 0) % 1000) + ")";
                    };
        }

        List<ExtractedFact> facts = new ArrayList<>();
        facts.add(ExtractedFact.detected(ProductField.MRP, mrpVal, 0.97, BoundingBox.of(64, 210, 150, 54)));
        facts.add(ExtractedFact.detected(ProductField.NET_QUANTITY, netQtyVal, 0.95, BoundingBox.of(64, 280, 180, 52)));
        facts.add(ExtractedFact.detected(ProductField.MANUFACTURER, mfrVal, 0.93, BoundingBox.of(60, 355, 320, 48)));
        facts.add(ExtractedFact.detected(ProductField.ORIGIN, "India", 0.92, BoundingBox.of(60, 410, 140, 44)));
        facts.add(ExtractedFact.detected(ProductField.COMMODITY_NAME, commodityVal, 0.96, BoundingBox.of(58, 120, 380, 62)));

        switch (variant) {
            case 0 -> {
                facts.add(ExtractedFact.notDetected(ProductField.CONSUMER_CARE, 0.91));
                facts.add(ExtractedFact.detected(ProductField.MANUFACTURE_DATE, "03/2026", 0.89,
                        BoundingBox.of(240, 410, 160, 44)));
            }
            case 1 -> {
                facts.add(ExtractedFact.detected(ProductField.CONSUMER_CARE, "care@" + mfrVal.toLowerCase().replaceAll("[^a-z]", "") + ".example / 1800-123-456",
                        0.90, BoundingBox.of(58, 470, 400, 56)));
                facts.add(ExtractedFact.detected(ProductField.MANUFACTURE_DATE, "03/2026", 0.88,
                        BoundingBox.of(240, 410, 160, 44)));
            }
            default -> {
                facts.add(ExtractedFact.detected(ProductField.CONSUMER_CARE, "car…@contact…", 0.41,
                        BoundingBox.of(58, 470, 400, 56)));
                facts.add(ExtractedFact.notDetected(ProductField.MANUFACTURE_DATE, 0.52));
            }
        }

        List<String> warnings = variant == 2
                ? List.of("Low-contrast region detected on the lower panel; some text may be unreliable.")
                : List.of();

        AIAnalysisResult result = new AIAnalysisResult(
                inspectionId,
                facts,
                AIAnalysisResult.PROVIDER_MOCK,
                MODEL_VERSION,
                System.currentTimeMillis() - startedAt,
                warnings);

        log.debug("Mock AI produced {} facts for inspection {} (variant {})", facts.size(), inspectionId, variant);
        return result;
    }

    @Override
    public String providerName() {
        return AIAnalysisResult.PROVIDER_MOCK;
    }

    private int variantFor(UUID inspectionId) {
        if (inspectionId == null) {
            return 0;
        }
        return Math.floorMod(inspectionId.hashCode(), 3);
    }
}
