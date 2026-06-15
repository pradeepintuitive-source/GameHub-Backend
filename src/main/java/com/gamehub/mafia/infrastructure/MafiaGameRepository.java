package com.gamehub.mafia.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MafiaGameRepository extends JpaRepository<MafiaGameEntity, UUID> {

    Optional<MafiaGameEntity> findBySessionId(UUID sessionId);
}
