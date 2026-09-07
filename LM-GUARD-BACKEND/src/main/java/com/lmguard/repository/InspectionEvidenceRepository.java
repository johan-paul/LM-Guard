package com.lmguard.repository;

import com.lmguard.entity.InspectionEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InspectionEvidenceRepository extends JpaRepository<InspectionEvidence, UUID> {

    List<InspectionEvidence> findByInspectionIdOrderByCapturedAtAsc(UUID inspectionId);
}
