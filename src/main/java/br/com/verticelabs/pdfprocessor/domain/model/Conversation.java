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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversations")
@CompoundIndexes({
        @CompoundIndex(name = "participant_last_message", def = "{'participantUserIds': 1, 'lastMessageAt': -1}")
})
public class Conversation {

    @Id
    private String id;

    private String title;

    /** DIRECT | TENANT | SYSTEM */
    private String scope;

    private String tenantId;

    private String createdByUserId;
    private String createdByNome;
    private Instant createdAt;

    @Builder.Default
    private List<String> participantUserIds = new ArrayList<>();

    private Instant lastMessageAt;
    private String lastMessagePreview;
    private String lastSenderNome;

    /** userId -> última leitura */
    @Builder.Default
    private Map<String, Instant> lastReadAtByUser = new HashMap<>();

    public boolean isUnreadFor(String userId) {
        if (lastMessageAt == null) {
            return false;
        }
        Instant lastRead = lastReadAtByUser != null ? lastReadAtByUser.get(userId) : null;
        return lastRead == null || lastMessageAt.isAfter(lastRead);
    }
}
