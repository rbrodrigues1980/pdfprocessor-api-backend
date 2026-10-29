package br.com.verticelabs.pdfprocessor.interfaces.conversations;

import br.com.verticelabs.pdfprocessor.domain.model.Conversation;
import br.com.verticelabs.pdfprocessor.domain.model.ConversationMessage;
import br.com.verticelabs.pdfprocessor.interfaces.conversations.dto.ConversationAttachmentResponse;
import br.com.verticelabs.pdfprocessor.interfaces.conversations.dto.ConversationMessageResponse;
import br.com.verticelabs.pdfprocessor.interfaces.conversations.dto.ConversationResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ConversationMapper {

    public ConversationResponse toResponse(Conversation c, String currentUserId) {
        // Cópia mutável: Jackson/Mongo podem usar listas imutáveis (UnsupportedOperationException)
        List<String> participants = c.getParticipantUserIds() != null
                ? new ArrayList<>(c.getParticipantUserIds())
                : new ArrayList<>();
        return ConversationResponse.builder()
                .id(c.getId())
                .title(c.getTitle())
                .scope(c.getScope())
                .tenantId(c.getTenantId())
                .createdByUserId(c.getCreatedByUserId())
                .createdByNome(c.getCreatedByNome())
                .createdAt(c.getCreatedAt())
                .participantUserIds(participants)
                .participantCount(participants.size())
                .lastMessageAt(c.getLastMessageAt())
                .lastMessagePreview(c.getLastMessagePreview())
                .lastSenderNome(c.getLastSenderNome())
                .unread(c.isUnreadFor(currentUserId))
                .repliesAllowed(c.areRepliesAllowed())
                .build();
    }

    public ConversationMessageResponse toMessageResponse(ConversationMessage m, String currentUserId) {
        List<ConversationAttachmentResponse> attachments = m.getAttachments() == null
                ? Collections.emptyList()
                : m.getAttachments().stream()
                        .map(a -> ConversationAttachmentResponse.builder()
                                .fileId(a.getFileId())
                                .fileName(a.getFileName())
                                .contentType(a.getContentType())
                                .sizeBytes(a.getSizeBytes())
                                .build())
                        .collect(Collectors.toCollection(ArrayList::new));
        return ConversationMessageResponse.builder()
                .id(m.getId())
                .conversationId(m.getConversationId())
                .senderUserId(m.getSenderUserId())
                .senderNome(m.getSenderNome())
                .body(m.getBody())
                .createdAt(m.getCreatedAt())
                .attachments(attachments)
                .mine(currentUserId != null && currentUserId.equals(m.getSenderUserId()))
                .build();
    }
}
