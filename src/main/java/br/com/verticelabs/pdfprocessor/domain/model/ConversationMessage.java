package br.com.verticelabs.pdfprocessor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversation_messages")
@CompoundIndexes({
        @CompoundIndex(name = "conversation_created", def = "{'conversationId': 1, 'createdAt': 1}")
})
public class ConversationMessage {

    @Id
    private String id;

    private String conversationId;

    private String senderUserId;
    private String senderNome;

    private String body;
    private Instant createdAt;

    @Builder.Default
    private List<Attachment> attachments = new ArrayList<>();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attachment {
        private String fileId;
        private String fileName;
        private String contentType;
        private Long sizeBytes;
    }
}
