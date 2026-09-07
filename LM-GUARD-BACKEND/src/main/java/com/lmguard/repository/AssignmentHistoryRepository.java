package com.lmguard.repository;

import com.lmguard.entity.AssignmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssignmentHistoryRepository extends JpaRepository<AssignmentHistory, UUID> {

    @Query("""
            SELECT ah FROM AssignmentHistory ah
            LEFT JOIN FETCH ah.fromInspector
            LEFT JOIN FETCH ah.toInspector
            LEFT JOIN FETCH ah.fromZone
            LEFT JOIN FETCH ah.toZone
            LEFT JOIN FETCH ah.assignedBy
            WHERE ah.inspection.id = :inspectionId
            ORDER BY ah.createdAt DESC
            """)
    List<AssignmentHistory> findByInspectionIdOrderByCreatedAtDesc(@Param("inspectionId") UUID inspectionId);
}
