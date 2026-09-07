package com.lmguard.repository;

import com.lmguard.entity.ChecklistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChecklistEntryRepository extends JpaRepository<ChecklistEntry, UUID> {

    List<ChecklistEntry> findByInspectionIdOrderByCreatedAtAsc(UUID inspectionId);

    Optional<ChecklistEntry> findByInspection_IdAndItemCode(UUID inspectionId, String itemCode);

    @Query("SELECT COUNT(c) FROM ChecklistEntry c WHERE c.inspection.id = :inspectionId "
            + "AND c.result = com.lmguard.entity.enums.CheckResult.PENDING")
    long countPendingByInspectionId(@Param("inspectionId") UUID inspectionId);

    long countByInspection_Id(UUID inspectionId);
}
