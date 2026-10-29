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
@Document(collection = "notifications")
@CompoundIndexes({
        @CompoundIndex(name = "recipient_created", def = "{'recipientUserId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "recipient_read_created", def = "{'recipientUserId': 1, 'readAt': 1, 'createdAt': -1}")
})
public class Notification {

    @Id
    private String id;

    private String title;
    private String body;

    private String senderUserId;
    private String senderNome;

    private String recipientUserId;
    /** Tenant do destinatário (null se SUPER_ADMIN sem tenant). */
    private String tenantId;

    /** DIRECT | TENANT | SYSTEM */
    private String scope;

    private Instant createdAt;
    /** null = não lida */
    private Instant readAt;

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
