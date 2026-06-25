package com.gamehub.notification.infrastructure;

import com.gamehub.notification.application.NotificationService;
import com.gamehub.notification.domain.NotificationMessage;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StompNotificationService implements NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void sendToTopic(String destination, NotificationMessage message) {
        messagingTemplate.convertAndSend(destination, message);
    }

    @Override
    public void sendToUser(UUID userId, String destination, NotificationMessage message) {
        messagingTemplate.convertAndSendToUser(userId.toString(), destination, message);
    }
}
