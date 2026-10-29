package br.com.verticelabs.pdfprocessor.interfaces.conversations.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnreadConversationsResponse {
    private long unreadCount;
}
