package com.lmguard.repository;

import com.lmguard.entity.ExtractedField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExtractedFieldRepository extends JpaRepository<ExtractedField, UUID> {

    List<ExtractedField> findByInspectionIdOrderByFieldNameAsc(UUID inspectionId);

    void deleteByInspectionId(UUID inspectionId);
}
