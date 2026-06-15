package com.gamehub.audit.infrastructure;

import com.gamehub.audit.domain.AuditType;
import com.gamehub.persistence.infrastructure.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "audit_entries")
public class AuditEntryEntity extends BaseEntity {

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "audit_type", nullable = false, length = 60)
    private AuditType auditType;

    @Column(nullable = false, length = 255)
    private String message;

    @Column(columnDefinition = "TEXT")
    private String details;
}
