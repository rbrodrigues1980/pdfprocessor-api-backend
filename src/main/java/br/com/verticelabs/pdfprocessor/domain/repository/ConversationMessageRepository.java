package br.com.verticelabs.pdfprocessor.domain.repository;

import br.com.verticelabs.pdfprocessor.domain.model.ConversationMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface ConversationMessageRepository {

    Mono<ConversationMessage> save(ConversationMessage message);

    Mono<ConversationMessage> findById(String id);

    Flux<ConversationMessage> findByConversationId(String conversationId, int page, int size);

    Mono<Long> countByConversationId(String conversationId);

    Mono<Boolean> existsRecentDuplicate(
            String conversationId,
            String senderUserId,
            String body,
            Instant createdAfter);
}
