package com.gamehub.shared.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameEventRepository extends JpaRepository<GameEventEntity, UUID> {

    List<GameEventEntity> findTop100ByRoomIdOrderByOccurredAtDesc(UUID roomId);

    void deleteByRoomId(UUID roomId);

    void deleteBySessionId(UUID sessionId);
}
