package com.gamehub.ai.infrastructure;

import com.gamehub.ai.domain.AiType;
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
@Table(name = "ai_memory")
public class AiMemoryEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_type", nullable = false)
    private AiType aiType;

    @Column(name = "scope_id", nullable = false)
    private UUID scopeId;

    @Column(name = "memory_key", nullable = false, length = 120)
    private String memoryKey;

    @Column(name = "memory_value", nullable = false, columnDefinition = "TEXT")
    private String memoryValue;
}
