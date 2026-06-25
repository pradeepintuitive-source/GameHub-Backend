package com.gamehub.ai.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMemoryRepository extends JpaRepository<AiMemoryEntity, UUID> {

    List<AiMemoryEntity> findByScopeId(UUID scopeId);
}
