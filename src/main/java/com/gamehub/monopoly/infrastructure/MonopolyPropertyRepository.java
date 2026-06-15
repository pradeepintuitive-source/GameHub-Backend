package com.gamehub.monopoly.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonopolyPropertyRepository extends JpaRepository<MonopolyPropertyEntity, UUID> {

    void deleteBySessionId(UUID sessionId);

    List<MonopolyPropertyEntity> findBySessionId(UUID sessionId);
}
