package com.gamehub.websocket.infrastructure;

import com.gamehub.room.api.RoomDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LobbyBroadcaster {

    private final SimpMessagingTemplate template;

    public void broadcastRoom(UUID roomId, RoomDtos.RoomResponse room) {
        if (room == null) {
            // Still send an update to the topic to notify subscribers the room was removed
            template.convertAndSend("/topic/rooms/" + roomId, null);
            return;
        }
        template.convertAndSend("/topic/rooms/" + roomId, room);
    }
}
