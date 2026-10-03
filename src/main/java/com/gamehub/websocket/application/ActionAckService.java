package com.gamehub.websocket.application;

import com.gamehub.websocket.api.ActionAckMessage;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActionAckService {

    private static final String ACK_DESTINATION = "/queue/acks";

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry simpUserRegistry;

    public void sendAck(
            UUID userId,
            String requestId,
            String action,
            boolean success,
            String errorCode,
            String message,
            Map<String, Object> metadata) {
        if (userId == null || requestId == null || action == null) {
            return;
        }
        ActionAckMessage ack = new ActionAckMessage(
                "ACTION_ACK",
                requestId,
                action,
                success,
                Instant.now(),
                errorCode,
                message,
                metadata);
        boolean userRegistered = simpUserRegistry.getUser(userId.toString()) != null;
        log.info(
                "ACK DISPATCH: userId={} requestId={} action={} success={} destination={} registered={}",
                userId,
                requestId,
                action,
                success,
                ACK_DESTINATION,
                userRegistered);
        messagingTemplate.convertAndSendToUser(userId.toString(), ACK_DESTINATION, ack);
    }
}
