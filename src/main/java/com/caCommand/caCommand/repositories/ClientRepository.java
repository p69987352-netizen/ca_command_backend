package com.caCommand.caCommand.repositories;
import com.caCommand.caCommand.entities.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {
    Client findByPhoneNumber(String phoneNumber);
}