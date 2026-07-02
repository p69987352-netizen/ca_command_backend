package com.caCommand.caCommand.repositories;

import com.caCommand.caCommand.entities.ComplexityRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplexityRuleRepository extends JpaRepository<ComplexityRule, Long> {
    List<ComplexityRule> findByIsEnabledTrueOrderByPriorityDesc();
}
