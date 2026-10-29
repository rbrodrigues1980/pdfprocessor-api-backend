package br.com.verticelabs.pdfprocessor.application.conversations;

import br.com.verticelabs.pdfprocessor.domain.model.Conversation;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MarkConversationReadUseCase {

    private final ConversationRepository conversationRepository;

    public Mono<Conversation> execute(String conversationId) {
        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(userId -> conversationRepository.findById(conversationId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Conversa não encontrada")))
                        .flatMap(c -> {
                            if (c.getParticipantUserIds() == null
                                    || !c.getParticipantUserIds().contains(userId)) {
                                return Mono.error(new SecurityException(
                                        "Sem permissão para esta conversa"));
                            }
                            return conversationRepository.markRead(conversationId, userId, Instant.now());
                        }));
    }
}
