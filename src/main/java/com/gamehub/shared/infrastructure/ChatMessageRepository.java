package com.gamehub.shared.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    List<ChatMessageEntity> findTop100ByRoomIdOrderByCreatedAtAsc(UUID roomId);

    void deleteByRoomId(UUID roomId);
}
