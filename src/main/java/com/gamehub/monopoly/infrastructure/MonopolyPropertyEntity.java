package com.gamehub.monopoly.infrastructure;

import com.gamehub.persistence.infrastructure.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "monopoly_properties")
public class MonopolyPropertyEntity extends BaseEntity {

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "property_name", nullable = false, length = 120)
    private String propertyName;

    @Column(name = "owner_player_id")
    private UUID ownerPlayerId;

    @Column(nullable = false)
    private boolean mortgaged;

    @Column(nullable = false)
    private int houses;

    @Column(nullable = false)
    private boolean hotel;
}
