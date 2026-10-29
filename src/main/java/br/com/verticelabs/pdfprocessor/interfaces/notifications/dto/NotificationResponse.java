package br.com.verticelabs.pdfprocessor.interfaces.notifications.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class NotificationResponse {
    String id;
    String title;
    String body;
    String senderUserId;
    String senderNome;
    String recipientUserId;
    String tenantId;
    String scope;
    Instant createdAt;
    Instant readAt;
    boolean read;
    List<NotificationAttachmentResponse> attachments;
}
