package com.lmguard.repository;

import com.lmguard.entity.RiskScore;
import com.lmguard.entity.enums.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiskScoreRepository extends JpaRepository<RiskScore, UUID> {

    Optional<RiskScore> findByInspectionId(UUID inspectionId);

    List<RiskScore> findByProductIdOrderByCreatedAtDesc(UUID productId);

    long countByRiskLevel(RiskLevel riskLevel);

    @Query(value = """
            SELECT rs FROM RiskScore rs
            LEFT JOIN FETCH rs.product
            LEFT JOIN FETCH rs.inspection
            WHERE rs.riskLevel = :level
            ORDER BY rs.totalScore DESC, rs.createdAt DESC
            """,
            countQuery = "SELECT COUNT(rs) FROM RiskScore rs WHERE rs.riskLevel = :level")
    Page<RiskScore> findByLevel(@Param("level") RiskLevel level, Pageable pageable);

    @Query(value = """
            SELECT rs FROM RiskScore rs
            LEFT JOIN FETCH rs.product
            LEFT JOIN FETCH rs.inspection
            WHERE rs.totalScore >= :minScore
            ORDER BY rs.totalScore DESC, rs.createdAt DESC
            """,
            countQuery = "SELECT COUNT(rs) FROM RiskScore rs WHERE rs.totalScore >= :minScore")
    Page<RiskScore> findHighRisk(@Param("minScore") int minScore, Pageable pageable);
}
