package br.com.verticelabs.pdfprocessor.interfaces.notifications.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CreateNotificationResponse {
    int recipientCount;
    int attachmentCount;
    String message;
}
