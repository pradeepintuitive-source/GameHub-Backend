package com.gamehub.shared.infrastructure;

import com.gamehub.persistence.infrastructure.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "chat_messages")
public class ChatMessageEntity extends BaseEntity {

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "sender_user_id")
    private UUID senderUserId;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "sender_name", nullable = false, length = 120)
    private String senderName;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "system_message", nullable = false)
    private boolean systemMessage;

    @Column(name = "ai_message", nullable = false)
    private boolean aiMessage;
}
