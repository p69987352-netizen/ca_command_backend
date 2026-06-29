package com.caCommand.caCommand.repositories;

import com.caCommand.caCommand.entities.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {
    Optional<Client> findByPhoneNumber(String phoneNumber);

    @Query("SELECT c FROM Client c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Client> findByNameContainingIgnoreCase(@Param("name") String name);

    @Query("SELECT c FROM Client c WHERE c.phoneNumber LIKE CONCAT('%', :query, '%') OR LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Client> searchByPhoneOrName(@Param("query") String query);
}
