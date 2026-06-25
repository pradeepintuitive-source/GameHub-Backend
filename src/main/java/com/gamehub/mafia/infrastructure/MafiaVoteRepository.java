package com.gamehub.mafia.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MafiaVoteRepository extends JpaRepository<MafiaVoteEntity, UUID> {

    void deleteBySessionId(UUID sessionId);

    List<MafiaVoteEntity> findBySessionId(UUID sessionId);
}
