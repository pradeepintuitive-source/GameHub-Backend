package com.gamehub.audit.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEntryEntity, UUID> {

    List<AuditEntryEntity> findTop100ByRoomIdOrderByCreatedAtDesc(UUID roomId);
}
