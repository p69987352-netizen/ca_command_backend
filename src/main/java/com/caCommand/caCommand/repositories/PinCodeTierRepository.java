package com.caCommand.caCommand.repositories;

import com.caCommand.caCommand.entities.PinCodeTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PinCodeTierRepository extends JpaRepository<PinCodeTier, String> {
    Optional<PinCodeTier> findByPinCode(String pinCode);
}
