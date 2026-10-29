package br.com.verticelabs.pdfprocessor.interfaces.notifications.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class NotificationListResponse {
    List<NotificationResponse> items;
    long total;
    long unreadCount;
    int page;
    int size;
}
