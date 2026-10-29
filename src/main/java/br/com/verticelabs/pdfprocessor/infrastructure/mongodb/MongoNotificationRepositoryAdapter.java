package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.Notification;
import br.com.verticelabs.pdfprocessor.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class MongoNotificationRepositoryAdapter implements NotificationRepository {

    private final SpringDataNotificationRepository repository;
    private final ReactiveMongoTemplate mongoTemplate;

    @Override
    public Mono<Notification> save(Notification notification) {
        return repository.save(notification);
    }

    @Override
    public Flux<Notification> saveAll(Iterable<Notification> notifications) {
        return repository.saveAll(notifications);
    }

    @Override
    public Mono<Notification> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public Flux<Notification> findByRecipientUserId(String recipientUserId, boolean unreadOnly, int page, int size) {
        Query query = baseQuery(recipientUserId, unreadOnly);
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        query.skip((long) page * size).limit(size);
        return mongoTemplate.find(query, Notification.class);
    }

    @Override
    public Mono<Long> countByRecipientUserId(String recipientUserId, boolean unreadOnly) {
        return mongoTemplate.count(baseQuery(recipientUserId, unreadOnly), Notification.class);
    }

    @Override
    public Mono<Long> countUnreadByRecipientUserId(String recipientUserId) {
        return countByRecipientUserId(recipientUserId, true);
    }

    @Override
    public Mono<Notification> markRead(String id, String recipientUserId) {
        return repository.findById(id)
                .filter(n -> recipientUserId.equals(n.getRecipientUserId()))
                .flatMap(n -> {
                    n.setReadAt(Instant.now());
                    return repository.save(n);
                });
    }

    @Override
    public Mono<Long> markAllRead(String recipientUserId) {
        Query query = new Query(Criteria.where("recipientUserId").is(recipientUserId).and("readAt").is(null));
        Update update = new Update().set("readAt", Instant.now());
        return mongoTemplate.updateMulti(query, update, Notification.class)
                .map(result -> result.getModifiedCount());
    }

    @Override
    public Mono<Boolean> existsRecentDuplicate(
            String senderUserId,
            String recipientUserId,
            String title,
            String body,
            Instant createdAfter) {
        Query query = new Query(Criteria.where("senderUserId").is(senderUserId)
                .and("recipientUserId").is(recipientUserId)
                .and("title").is(title)
                .and("body").is(body)
                .and("createdAt").gte(createdAfter));
        return mongoTemplate.exists(query, Notification.class);
    }

    private static Query baseQuery(String recipientUserId, boolean unreadOnly) {
        Criteria criteria = Criteria.where("recipientUserId").is(recipientUserId);
        if (unreadOnly) {
            criteria = criteria.and("readAt").is(null);
        }
        return new Query(criteria);
    }
}
