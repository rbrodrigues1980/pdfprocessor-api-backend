package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.ConversationMessage;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationMessageRepository;
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

@Component
@RequiredArgsConstructor
public class MongoConversationMessageRepositoryAdapter implements ConversationMessageRepository {

    private final SpringDataConversationMessageRepository repository;
    private final ReactiveMongoTemplate mongoTemplate;

    @Override
    public Mono<ConversationMessage> save(ConversationMessage message) {
        return repository.save(ensureMutable(message));
    }

    @Override
    public Mono<ConversationMessage> findById(String id) {
        return repository.findById(id).map(MongoConversationMessageRepositoryAdapter::ensureMutable);
    }

    @Override
    public Flux<ConversationMessage> findByConversationId(String conversationId, int page, int size) {
        Query query = new Query(Criteria.where("conversationId").is(conversationId));
        query.with(Sort.by(Sort.Direction.ASC, "createdAt"));
        query.skip((long) page * size).limit(size);
        return mongoTemplate.find(query, ConversationMessage.class)
                .map(MongoConversationMessageRepositoryAdapter::ensureMutable);
    }

    @Override
    public Mono<Long> countByConversationId(String conversationId) {
        return mongoTemplate.count(
                new Query(Criteria.where("conversationId").is(conversationId)),
                ConversationMessage.class);
    }

    @Override
    public Mono<Boolean> existsRecentDuplicate(
            String conversationId,
            String senderUserId,
            String body,
            Instant createdAfter) {
        Query query = new Query(Criteria.where("conversationId").is(conversationId)
                .and("senderUserId").is(senderUserId)
                .and("body").is(body)
                .and("createdAt").gte(createdAfter));
        return mongoTemplate.exists(query, ConversationMessage.class);
    }

    private static ConversationMessage ensureMutable(ConversationMessage m) {
        if (m == null) {
            return null;
        }
        if (m.getAttachments() != null) {
            m.setAttachments(new ArrayList<>(m.getAttachments()));
        } else {
            m.setAttachments(new ArrayList<>());
        }
        return m;
    }
}
