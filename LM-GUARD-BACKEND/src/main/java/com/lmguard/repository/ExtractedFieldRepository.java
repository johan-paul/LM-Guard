package com.lmguard.repository;

import com.lmguard.entity.ExtractedField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExtractedFieldRepository extends JpaRepository<ExtractedField, UUID> {

    List<ExtractedField> findByInspectionIdOrderByFieldNameAsc(UUID inspectionId);

    void deleteByInspectionId(UUID inspectionId);

    /** Used when replacing AI-derived fields on a fresh /analyze: excludes fields an inspector
     * submitted directly (see ProductField.INSPECTOR_MEASURED_FIELDS), which a photo
     * re-analysis has nothing to say about and must not delete. */
    void deleteByInspectionIdAndFieldNameNotIn(UUID inspectionId, Collection<String> fieldNamesToKeep);

    Optional<ExtractedField> findByInspectionIdAndFieldName(UUID inspectionId, String fieldName);
}
