package com.caCommand.caCommand.repositories;

import com.caCommand.caCommand.entities.PricingRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {
    Optional<PricingRule> findByServiceType(String serviceType);
    Optional<PricingRule> findByServiceTypeAndIsActiveTrue(String serviceType);
}
