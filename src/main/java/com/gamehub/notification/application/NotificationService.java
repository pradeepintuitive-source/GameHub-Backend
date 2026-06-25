package com.gamehub.notification.application;

import com.gamehub.notification.domain.NotificationMessage;
import java.util.UUID;

public interface NotificationService {

    void sendToTopic(String destination, NotificationMessage message);

    void sendToUser(UUID userId, String destination, NotificationMessage message);
}
