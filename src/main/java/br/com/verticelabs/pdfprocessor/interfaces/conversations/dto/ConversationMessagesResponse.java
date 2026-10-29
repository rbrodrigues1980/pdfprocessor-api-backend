package br.com.verticelabs.pdfprocessor.interfaces.conversations.dto;

import com.fasterxml.jackson.annotation.JsonGetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessagesResponse {
    private String conversationId;
    private String title;
    private String createdByUserId;
    @Builder.Default
    private boolean repliesAllowed = true;
    @Builder.Default
    private List<ConversationMessageResponse> items = new ArrayList<>();
    private long total;
    private int page;
    private int size;

    @JsonGetter("items")
    public List<ConversationMessageResponse> getItems() {
        return items != null ? new ArrayList<>(items) : new ArrayList<>();
    }
}
