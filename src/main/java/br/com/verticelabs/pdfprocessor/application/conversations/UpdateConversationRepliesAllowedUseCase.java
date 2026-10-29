package br.com.verticelabs.pdfprocessor.application.conversations;

import br.com.verticelabs.pdfprocessor.domain.model.Conversation;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UpdateConversationRepliesAllowedUseCase {

    private final ConversationRepository conversationRepository;

    public Mono<Conversation> execute(String conversationId, boolean repliesAllowed) {
        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(userId -> conversationRepository.findById(conversationId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Conversa não encontrada")))
                        .flatMap(conversation -> {
                            if (!userId.equals(conversation.getCreatedByUserId())) {
                                return Mono.error(new SecurityException(
                                        "Apenas o autor pode alterar se respostas são permitidas"));
                            }
                            conversation.setRepliesAllowed(repliesAllowed);
                            return conversationRepository.save(conversation);
                        }));
    }
}
