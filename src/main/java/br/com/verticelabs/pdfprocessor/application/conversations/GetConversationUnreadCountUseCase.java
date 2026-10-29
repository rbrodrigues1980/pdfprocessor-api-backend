package br.com.verticelabs.pdfprocessor.application.conversations;

import br.com.verticelabs.pdfprocessor.domain.repository.ConversationRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class GetConversationUnreadCountUseCase {

    private final ConversationRepository conversationRepository;

    public Mono<Long> execute() {
        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(conversationRepository::countUnreadByParticipantUserId);
    }
}
