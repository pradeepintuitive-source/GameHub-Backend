package com.gamehub.player.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<PlayerEntity, UUID> {

    List<PlayerEntity> findByRoomIdOrderBySeatOrder(UUID roomId);

    Optional<PlayerEntity> findByRoomIdAndUserId(UUID roomId, UUID userId);

    long countByRoomId(UUID roomId);
}
