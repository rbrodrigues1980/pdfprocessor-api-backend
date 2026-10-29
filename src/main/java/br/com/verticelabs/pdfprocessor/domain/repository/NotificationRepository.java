package br.com.verticelabs.pdfprocessor.domain.repository;

import br.com.verticelabs.pdfprocessor.domain.model.Notification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface NotificationRepository {

    Mono<Notification> save(Notification notification);

    Flux<Notification> saveAll(Iterable<Notification> notifications);

    Mono<Notification> findById(String id);

    Flux<Notification> findByRecipientUserId(String recipientUserId, boolean unreadOnly, int page, int size);

    Mono<Long> countByRecipientUserId(String recipientUserId, boolean unreadOnly);

    Mono<Long> countUnreadByRecipientUserId(String recipientUserId);

    Mono<Notification> markRead(String id, String recipientUserId);

    Mono<Long> markAllRead(String recipientUserId);

    /**
     * Existe aviso idêntico recente (mesmo remetente/destinatário/título/corpo)?
     * Usado para absorver POSTs duplicados do mesmo clique no cliente.
     */
    Mono<Boolean> existsRecentDuplicate(
            String senderUserId,
            String recipientUserId,
            String title,
            String body,
            java.time.Instant createdAfter);
}
