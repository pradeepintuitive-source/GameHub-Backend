package com.gamehub.player.infrastructure;

import com.gamehub.config.infrastructure.MapStructCentralConfig;
import com.gamehub.player.api.UserDtos;
import org.mapstruct.Mapper;

@Mapper(config = MapStructCentralConfig.class)
public interface UserMapper {

    UserDtos.ProfileResponse toProfileResponse(ProfileEntity profileEntity);

    UserDtos.StatisticsResponse toStatisticsResponse(StatisticsEntity statisticsEntity);
}
