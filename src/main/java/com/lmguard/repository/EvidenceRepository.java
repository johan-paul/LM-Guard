package com.lmguard.repository;

import com.lmguard.entity.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {

    List<Evidence> findByViolationIdOrderByCreatedAtAsc(UUID violationId);

    List<Evidence> findByInspectionIdOrderByCreatedAtAsc(UUID inspectionId);
}
