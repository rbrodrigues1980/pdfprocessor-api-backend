package br.com.verticelabs.pdfprocessor.domain.repository;

import br.com.verticelabs.pdfprocessor.domain.model.Conversation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface ConversationRepository {

    Mono<Conversation> save(Conversation conversation);

    Mono<Conversation> findById(String id);

    Flux<Conversation> findByParticipantUserId(String userId, boolean unreadOnly, int page, int size);

    Mono<Long> countByParticipantUserId(String userId, boolean unreadOnly);

    Mono<Long> countUnreadByParticipantUserId(String userId);

    Mono<Conversation> markRead(String conversationId, String userId, Instant readAt);

    Mono<Boolean> existsRecentDuplicate(
            String createdByUserId,
            String title,
            String firstBodyPreview,
            Instant createdAfter);
}
