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
public class ConversationMessageResponse {
    private String id;
    private String conversationId;
    private String senderUserId;
    private String senderNome;
    private String body;
    private Instant createdAt;
    @Builder.Default
    private List<ConversationAttachmentResponse> attachments = new ArrayList<>();
    private boolean mine;

    @JsonGetter("attachments")
    public List<ConversationAttachmentResponse> getAttachments() {
        return attachments != null ? new ArrayList<>(attachments) : new ArrayList<>();
    }
}
