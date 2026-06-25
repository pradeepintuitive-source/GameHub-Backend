package com.gamehub.mafia.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MafiaRoleRepository extends JpaRepository<MafiaRoleEntity, UUID> {

    void deleteBySessionId(UUID sessionId);

    List<MafiaRoleEntity> findBySessionId(UUID sessionId);
}
