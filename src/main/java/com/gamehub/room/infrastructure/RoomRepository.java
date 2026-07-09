package com.gamehub.room.infrastructure;

import com.gamehub.room.domain.RoomState;
import com.gamehub.room.domain.RoomVisibility;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<RoomEntity, UUID> {

    Optional<RoomEntity> findByRoomCode(String roomCode);

    Optional<RoomEntity> findByRoomCodeIgnoreCase(String roomCode);

    List<RoomEntity> findByVisibilityAndStateIn(RoomVisibility visibility, List<RoomState> states);
}
