package com.caCommand.caCommand.repositories;

import com.caCommand.caCommand.entities.PricingAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingAnalysisRepository extends JpaRepository<PricingAnalysis, String> {
}
