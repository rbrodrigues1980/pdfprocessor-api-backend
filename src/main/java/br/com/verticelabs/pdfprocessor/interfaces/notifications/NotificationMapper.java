package br.com.verticelabs.pdfprocessor.interfaces.notifications;

import br.com.verticelabs.pdfprocessor.domain.model.Notification;
import br.com.verticelabs.pdfprocessor.interfaces.notifications.dto.NotificationAttachmentResponse;
import br.com.verticelabs.pdfprocessor.interfaces.notifications.dto.NotificationResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification n) {
        List<NotificationAttachmentResponse> attachments = n.getAttachments() == null
                ? Collections.emptyList()
                : n.getAttachments().stream()
                        .map(a -> NotificationAttachmentResponse.builder()
                                .fileId(a.getFileId())
                                .fileName(a.getFileName())
                                .contentType(a.getContentType())
                                .sizeBytes(a.getSizeBytes())
                                .build())
                        .collect(Collectors.toList());
        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .body(n.getBody())
                .senderUserId(n.getSenderUserId())
                .senderNome(n.getSenderNome())
                .recipientUserId(n.getRecipientUserId())
                .tenantId(n.getTenantId())
                .scope(n.getScope())
                .createdAt(n.getCreatedAt())
                .readAt(n.getReadAt())
                .read(n.getReadAt() != null)
                .attachments(attachments)
                .build();
    }
}
