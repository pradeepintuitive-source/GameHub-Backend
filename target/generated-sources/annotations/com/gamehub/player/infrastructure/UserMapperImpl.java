package com.gamehub.player.infrastructure;

import com.gamehub.player.api.UserDtos;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-15T16:54:51+0530",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.0.v20260407-0427, environment: Java 21.0.10 (Eclipse Adoptium)"
)
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public UserDtos.ProfileResponse toProfileResponse(ProfileEntity profileEntity) {
        if ( profileEntity == null ) {
            return null;
        }

        String displayName = null;
        String avatarUrl = null;
        String locale = null;

        displayName = profileEntity.getDisplayName();
        avatarUrl = profileEntity.getAvatarUrl();
        locale = profileEntity.getLocale();

        UserDtos.ProfileResponse profileResponse = new UserDtos.ProfileResponse( displayName, avatarUrl, locale );

        return profileResponse;
    }

    @Override
    public UserDtos.StatisticsResponse toStatisticsResponse(StatisticsEntity statisticsEntity) {
        if ( statisticsEntity == null ) {
            return null;
        }

        int gamesPlayed = 0;
        int wins = 0;
        int losses = 0;
        long playTimeSeconds = 0L;

        gamesPlayed = statisticsEntity.getGamesPlayed();
        wins = statisticsEntity.getWins();
        losses = statisticsEntity.getLosses();
        playTimeSeconds = statisticsEntity.getPlayTimeSeconds();

        UserDtos.StatisticsResponse statisticsResponse = new UserDtos.StatisticsResponse( gamesPlayed, wins, losses, playTimeSeconds );

        return statisticsResponse;
    }
}
