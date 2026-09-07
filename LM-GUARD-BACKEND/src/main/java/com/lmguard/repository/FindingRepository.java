package com.lmguard.repository;

import com.lmguard.entity.Finding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FindingRepository extends JpaRepository<Finding, UUID> {

    List<Finding> findByInspectionIdOrderBySequenceAsc(UUID inspectionId);

    long countByInspection_Id(UUID inspectionId);
}
