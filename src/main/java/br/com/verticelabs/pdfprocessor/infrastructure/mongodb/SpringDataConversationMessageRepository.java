package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.ConversationMessage;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataConversationMessageRepository
        extends ReactiveMongoRepository<ConversationMessage, String> {
}
