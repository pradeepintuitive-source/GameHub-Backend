package com.gamehub.shared.application;

import com.gamehub.audit.application.AuditService;
import com.gamehub.audit.domain.AuditType;
import com.gamehub.common.domain.BusinessRuleViolationException;
import com.gamehub.notification.application.NotificationService;
import com.gamehub.notification.domain.NotificationMessage;
import com.gamehub.player.application.UserService;
import com.gamehub.player.infrastructure.PlayerRepository;
import com.gamehub.room.infrastructure.RoomRepository;
import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import com.gamehub.shared.api.ChatDtos;
import com.gamehub.shared.domain.GameEventType;
import com.gamehub.shared.infrastructure.ChatMessageEntity;
import com.gamehub.shared.infrastructure.ChatMessageRepository;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final GameEventService gameEventService;
    private final UserService userService;

    @Value("${gamehub.chat.max-message-length}")
    private int maxMessageLength;

    @Value("${gamehub.chat.max-messages-per-minute}")
    private int maxMessagesPerMinute;

    private final Map<UUID, Deque<Instant>> rateLimitState = new ConcurrentHashMap<>();

    @Transactional
    public ChatDtos.ChatMessageResponse send(
            UUID roomId,
            GameHubUserPrincipal principal,
            ChatDtos.SendChatMessageRequest request) {
        roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessRuleViolationException("Room not found"));
        playerRepository.findByRoomIdAndUserId(roomId, principal.userId())
                .orElseThrow(() -> new BusinessRuleViolationException("Player is not part of this room"));
        enforceRateLimit(principal.userId());
        if (request.content().length() > maxMessageLength) {
            throw new BusinessRuleViolationException("Chat message exceeds max length");
        }

        var currentUser = userService.getCurrentUser(principal);
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(UUID.randomUUID());
        entity.setRoomId(roomId);
        entity.setSenderUserId(principal.userId());
        entity.setTargetUserId(request.targetUserId());
        entity.setSenderName(currentUser.profile().displayName());
        entity.setContent(request.content().trim());
        entity.setSystemMessage(false);
        entity.setAiMessage(false);
        chatMessageRepository.save(entity);

        ChatDtos.ChatMessageResponse response = new ChatDtos.ChatMessageResponse(
                entity.getId(),
                entity.getRoomId(),
                entity.getSenderUserId(),
                entity.getTargetUserId(),
                entity.getSenderName(),
                entity.getContent(),
                entity.isSystemMessage(),
                entity.isAiMessage(),
                entity.getCreatedAt());

        auditService.record(AuditType.CHAT, roomId, null, principal.userId(), "Chat message sent", entity.getContent());
        gameEventService.record(roomId, null, GameEventType.CHAT_MESSAGE_SENT, principal.userId(), entity.getContent());
        notificationService.sendToTopic(
                "/topic/rooms/" + roomId + "/chat",
                new NotificationMessage("CHAT_MESSAGE", roomId, null, response, Instant.now()));
        if (request.targetUserId() != null) {
            notificationService.sendToUser(
                    request.targetUserId(),
                    "/queue/private",
                    new NotificationMessage("PRIVATE_CHAT_MESSAGE", roomId, null, response, Instant.now()));
        }
        return response;
    }

    @Transactional(readOnly = true)
    public java.util.List<ChatDtos.ChatMessageResponse> history(UUID roomId, GameHubUserPrincipal principal) {
        playerRepository.findByRoomIdAndUserId(roomId, principal.userId())
                .orElseThrow(() -> new BusinessRuleViolationException("Player is not part of this room"));
        return chatMessageRepository.findTop100ByRoomIdOrderByCreatedAtAsc(roomId)
                .stream()
                .map(entity -> new ChatDtos.ChatMessageResponse(
                        entity.getId(),
                        entity.getRoomId(),
                        entity.getSenderUserId(),
                        entity.getTargetUserId(),
                        entity.getSenderName(),
                        entity.getContent(),
                        entity.isSystemMessage(),
                        entity.isAiMessage(),
                        entity.getCreatedAt()))
                .toList();
    }

    private void enforceRateLimit(UUID userId) {
        Instant cutoff = Instant.now().minusSeconds(60);
        Deque<Instant> timestamps = rateLimitState.computeIfAbsent(userId, key -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxMessagesPerMinute) {
                throw new BusinessRuleViolationException("Chat rate limit exceeded");
            }
            timestamps.addLast(Instant.now());
        }
    }
}
