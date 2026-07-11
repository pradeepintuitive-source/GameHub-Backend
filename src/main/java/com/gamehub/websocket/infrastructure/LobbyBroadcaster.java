package com.gamehub.websocket.infrastructure;

import com.gamehub.room.api.RoomDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LobbyBroadcaster {

    private final SimpMessagingTemplate template;

    public void broadcastRoom(UUID roomId, RoomDtos.RoomResponse room) {
        if (room == null) {
            // Send empty map to signal room removal — avoids null ambiguity
            template.convertAndSend("/topic/rooms/" + roomId, Map.of());
            return;
        }
        template.convertAndSend("/topic/rooms/" + roomId, (Object) room);
    }

    public void broadcastRoomClosed(UUID roomId) {
        template.convertAndSend("/topic/rooms/" + roomId, Map.of("type", "ROOM_CLOSED", "roomId", roomId.toString()));
    }
}