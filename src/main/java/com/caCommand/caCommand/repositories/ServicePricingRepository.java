package com.caCommand.caCommand.repositories;

import com.caCommand.caCommand.entities.ServicePricing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServicePricingRepository extends JpaRepository<ServicePricing, String> {
    List<ServicePricing> findByCategory(String category);
    List<ServicePricing> findByIsActiveTrue();
    Optional<ServicePricing> findByServiceKeyIgnoreCase(String serviceKey);
}
