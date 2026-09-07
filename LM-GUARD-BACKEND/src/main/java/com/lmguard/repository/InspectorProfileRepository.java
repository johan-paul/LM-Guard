package com.lmguard.repository;

import com.lmguard.entity.InspectorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InspectorProfileRepository extends JpaRepository<InspectorProfile, UUID> {

    /** Loads the profile plus its user and zone in one round trip. */
    @Query("""
            SELECT ip FROM InspectorProfile ip
            LEFT JOIN FETCH ip.user
            LEFT JOIN FETCH ip.zone
            WHERE ip.officerCode = :officerCode
            """)
    Optional<InspectorProfile> findDetailedByOfficerCode(@Param("officerCode") String officerCode);

    @Query("""
            SELECT ip FROM InspectorProfile ip
            LEFT JOIN FETCH ip.user
            LEFT JOIN FETCH ip.zone
            WHERE ip.user.id = :userId
            """)
    Optional<InspectorProfile> findDetailedByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT ip FROM InspectorProfile ip
            LEFT JOIN FETCH ip.user
            LEFT JOIN FETCH ip.zone
            ORDER BY ip.zone.name ASC NULLS LAST, ip.user.name ASC
            """)
    List<InspectorProfile> findAllDetailed();

    boolean existsByOfficerCode(String officerCode);

    /** All officer codes in use, so a new one can continue the numeric sequence in Java. */
    @Query("SELECT ip.officerCode FROM InspectorProfile ip")
    List<String> findAllOfficerCodes();

    /** Officer headcount per zone, for the Analytics "Regional insights" panel. */
    @Query("SELECT ip.zone.name, COUNT(ip) FROM InspectorProfile ip WHERE ip.zone IS NOT NULL GROUP BY ip.zone.name")
    List<Object[]> countGroupedByZoneName();
}
