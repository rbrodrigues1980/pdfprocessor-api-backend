package br.com.verticelabs.pdfprocessor.interfaces.notifications.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NotificationAttachmentResponse {
    String fileId;
    String fileName;
    String contentType;
    Long sizeBytes;
}
