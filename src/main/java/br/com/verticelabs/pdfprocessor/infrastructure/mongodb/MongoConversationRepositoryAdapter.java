package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.Conversation;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;

@Component
@RequiredArgsConstructor
public class MongoConversationRepositoryAdapter implements ConversationRepository {

    private final SpringDataConversationRepository repository;
    private final ReactiveMongoTemplate mongoTemplate;

    @Override
    public Mono<Conversation> save(Conversation conversation) {
        return repository.save(ensureMutable(conversation));
    }

    @Override
    public Mono<Conversation> findById(String id) {
        return repository.findById(id).map(MongoConversationRepositoryAdapter::ensureMutable);
    }

    @Override
    public Flux<Conversation> findByParticipantUserId(String userId, boolean unreadOnly, int page, int size) {
        // unreadOnly é aplicado no use case (compara lastMessageAt x lastReadAtByUser)
        Query query = new Query(Criteria.where("participantUserIds").is(userId));
        query.with(Sort.by(Sort.Direction.DESC, "lastMessageAt"));
        if (!unreadOnly) {
            query.skip((long) page * size).limit(size);
        }
        return mongoTemplate.find(query, Conversation.class)
                .map(MongoConversationRepositoryAdapter::ensureMutable);
    }

    @Override
    public Mono<Long> countByParticipantUserId(String userId, boolean unreadOnly) {
        // Contagem exata de unread fica no use case; aqui conta participação.
        Query query = new Query(Criteria.where("participantUserIds").is(userId));
        return mongoTemplate.count(query, Conversation.class);
    }

    @Override
    public Mono<Long> countUnreadByParticipantUserId(String userId) {
        return findByParticipantUserId(userId, false, 0, Integer.MAX_VALUE)
                .filter(c -> c.isUnreadFor(userId))
                .count();
    }

    @Override
    public Mono<Conversation> markRead(String conversationId, String userId, Instant readAt) {
        return repository.findById(conversationId)
                .filter(c -> c.getParticipantUserIds() != null && c.getParticipantUserIds().contains(userId))
                .flatMap(c -> {
                    // Mongo pode devolver Map imutável — sempre copiar antes de put
                    HashMap<String, Instant> reads = c.getLastReadAtByUser() != null
                            ? new HashMap<>(c.getLastReadAtByUser())
                            : new HashMap<>();
                    reads.put(userId, readAt);
                    c.setLastReadAtByUser(reads);
                    return repository.save(c);
                });
    }

    @Override
    public Mono<Boolean> existsRecentDuplicate(
            String createdByUserId,
            String title,
            String firstBodyPreview,
            Instant createdAfter) {
        Query query = new Query(Criteria.where("createdByUserId").is(createdByUserId)
                .and("title").is(title)
                .and("lastMessagePreview").is(firstBodyPreview)
                .and("createdAt").gte(createdAfter));
        return mongoTemplate.exists(query, Conversation.class);
    }

    private static Conversation ensureMutable(Conversation c) {
        if (c == null) {
            return null;
        }
        if (c.getParticipantUserIds() != null) {
            c.setParticipantUserIds(new ArrayList<>(c.getParticipantUserIds()));
        } else {
            c.setParticipantUserIds(new ArrayList<>());
        }
        if (c.getLastReadAtByUser() != null) {
            c.setLastReadAtByUser(new HashMap<>(c.getLastReadAtByUser()));
        } else {
            c.setLastReadAtByUser(new HashMap<>());
        }
        return c;
    }
}
