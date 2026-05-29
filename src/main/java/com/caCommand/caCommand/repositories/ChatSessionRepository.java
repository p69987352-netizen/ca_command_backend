package com.caCommand.caCommand.repositories;

import com.caCommand.caCommand.entities.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {
    // Spring Boot automatically implements this! 
    // It gives us methods like save(), findById(), delete() etc.
}