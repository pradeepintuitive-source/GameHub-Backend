package com.gamehub.monopoly.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonopolyGameRepository extends JpaRepository<MonopolyGameEntity, UUID> {

    Optional<MonopolyGameEntity> findBySessionId(UUID sessionId);
}
