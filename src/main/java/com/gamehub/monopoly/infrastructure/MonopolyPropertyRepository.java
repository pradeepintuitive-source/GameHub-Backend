package com.gamehub.monopoly.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonopolyPropertyRepository extends JpaRepository<MonopolyPropertyEntity, UUID> {

    @Modifying
    @Query("DELETE FROM MonopolyPropertyEntity p WHERE p.sessionId = :sessionId")
    void deleteBySessionId(@Param("sessionId") UUID sessionId);

    List<MonopolyPropertyEntity> findBySessionId(UUID sessionId);
}
