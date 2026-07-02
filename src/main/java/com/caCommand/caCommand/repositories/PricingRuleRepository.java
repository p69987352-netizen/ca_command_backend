package com.caCommand.caCommand.repositories;

import com.caCommand.caCommand.entities.PricingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {
    Optional<PricingRule> findByServiceTypeAndIsActiveTrue(String serviceType);
}
