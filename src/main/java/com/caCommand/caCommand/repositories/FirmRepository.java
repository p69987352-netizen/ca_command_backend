package com.caCommand.caCommand.repositories;
import com.caCommand.caCommand.entities.Firm;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface FirmRepository extends JpaRepository<Firm, UUID> {}
