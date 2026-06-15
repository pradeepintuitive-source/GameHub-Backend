package com.gamehub.shared.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSessionRepository extends JpaRepository<GameSessionEntity, UUID> {

    List<GameSessionEntity> findByRoomIdOrderByCreatedAtDesc(UUID roomId);

    Optional<GameSessionEntity> findByRoomIdAndStatus(UUID roomId, com.gamehub.shared.domain.SessionStatus status);
}
