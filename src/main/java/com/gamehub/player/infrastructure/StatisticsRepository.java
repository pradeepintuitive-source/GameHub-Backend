package com.gamehub.player.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatisticsRepository extends JpaRepository<StatisticsEntity, UUID> {

    Optional<StatisticsEntity> findByUserId(UUID userId);
}
