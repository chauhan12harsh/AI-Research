package com.harsh.research.chat;

import com.harsh.research.common.SoftDeletableEntity;
import com.harsh.research.workspace.Workspace;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "chat_conversations")
@Getter
@Setter
@NoArgsConstructor
public class Conversation extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Workspace workspace;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String title;

    public static Conversation create(Workspace workspace, UUID userId, String title) {
        Conversation conversation = new Conversation();
        conversation.setWorkspace(workspace);
        conversation.setUserId(userId);
        conversation.setTitle(title);
        return conversation;
    }
}
