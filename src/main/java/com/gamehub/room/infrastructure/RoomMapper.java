package com.gamehub.room.infrastructure;

import com.gamehub.config.infrastructure.MapStructCentralConfig;
import com.gamehub.player.infrastructure.PlayerEntity;
import com.gamehub.room.api.RoomDtos;
import org.mapstruct.Mapper;

@Mapper(config = MapStructCentralConfig.class)
public interface RoomMapper {

    RoomDtos.PlayerSummary toPlayerSummary(PlayerEntity playerEntity);
}
