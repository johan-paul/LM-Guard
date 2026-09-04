package com.lmguard.repository;

import com.lmguard.entity.OnlineListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OnlineListingRepository extends JpaRepository<OnlineListing, UUID> {

    List<OnlineListing> findByProductIdOrderByCapturedAtDesc(UUID productId);

    Optional<OnlineListing> findFirstByProductIdOrderByCapturedAtDesc(UUID productId);
}
