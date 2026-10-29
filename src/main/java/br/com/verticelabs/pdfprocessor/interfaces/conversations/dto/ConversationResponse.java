package br.com.verticelabs.pdfprocessor.interfaces.conversations.dto;

import com.fasterxml.jackson.annotation.JsonGetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {
    private String id;
    private String title;
    private String scope;
    private String tenantId;
    private String createdByUserId;
    private String createdByNome;
    private Instant createdAt;
    @Builder.Default
    private List<String> participantUserIds = new ArrayList<>();
    private int participantCount;
    private Instant lastMessageAt;
    private String lastMessagePreview;
    private String lastSenderNome;
    private boolean unread;

    @JsonGetter("participantUserIds")
    public List<String> getParticipantUserIds() {
        return participantUserIds != null ? new ArrayList<>(participantUserIds) : new ArrayList<>();
    }
}
