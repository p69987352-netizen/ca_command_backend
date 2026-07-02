package com.caCommand.caCommand.repositories;

import com.caCommand.caCommand.entities.TisCategoryMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TisCategoryMappingRepository extends JpaRepository<TisCategoryMapping, Long> {
    List<TisCategoryMapping> findByEnabledTrueOrderByDisplayOrderAsc();
}
