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
public class ConversationListResponse {
    @Builder.Default
    private List<ConversationResponse> items = new ArrayList<>();
    private long total;
    private long unreadCount;
    private int page;
    private int size;

    @JsonGetter("items")
    public List<ConversationResponse> getItems() {
        return items != null ? new ArrayList<>(items) : new ArrayList<>();
    }
}
