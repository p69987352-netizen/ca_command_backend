package com.caCommand.caCommand.repositories;

import com.caCommand.caCommand.entities.DocumentCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentCacheRepository extends JpaRepository<DocumentCache, String> {
}
