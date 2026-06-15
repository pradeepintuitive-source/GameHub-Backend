package com.gamehub.room.infrastructure;

import com.gamehub.ai.domain.AiDifficulty;
import com.gamehub.ai.domain.AiType;
import com.gamehub.player.infrastructure.PlayerEntity;
import com.gamehub.room.api.RoomDtos;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-15T16:54:51+0530",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.0.v20260407-0427, environment: Java 21.0.10 (Eclipse Adoptium)"
)
@Component
public class RoomMapperImpl implements RoomMapper {

    @Override
    public RoomDtos.PlayerSummary toPlayerSummary(PlayerEntity playerEntity) {
        if ( playerEntity == null ) {
            return null;
        }

        UUID id = null;
        UUID userId = null;
        String displayName = null;
        boolean connected = false;
        boolean aiControlled = false;
        AiType aiType = null;
        AiDifficulty aiDifficulty = null;
        int seatOrder = 0;

        id = playerEntity.getId();
        userId = playerEntity.getUserId();
        displayName = playerEntity.getDisplayName();
        connected = playerEntity.isConnected();
        aiControlled = playerEntity.isAiControlled();
        aiType = playerEntity.getAiType();
        aiDifficulty = playerEntity.getAiDifficulty();
        seatOrder = playerEntity.getSeatOrder();

        RoomDtos.PlayerSummary playerSummary = new RoomDtos.PlayerSummary( id, userId, displayName, connected, aiControlled, aiType, aiDifficulty, seatOrder );

        return playerSummary;
    }
}
