package com.lmguard.repository;

import com.lmguard.entity.Rule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RuleRepository extends JpaRepository<Rule, UUID> {

    List<Rule> findByVersionAndActiveTrueOrderByRuleCodeAsc(String version);

    List<Rule> findByVersionOrderByRuleCodeAsc(String version);

    Optional<Rule> findByRuleCodeAndVersion(String ruleCode, String version);

    boolean existsByVersion(String version);

    long countByVersionAndActiveTrue(String version);
}
