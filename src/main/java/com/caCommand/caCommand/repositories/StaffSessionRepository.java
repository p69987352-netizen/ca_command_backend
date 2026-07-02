package com.caCommand.caCommand.repositories;

import com.caCommand.caCommand.entities.StaffSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffSessionRepository extends JpaRepository<StaffSession, Long> {
    Optional<StaffSession> findByStaffId(java.util.UUID staffId);
}
