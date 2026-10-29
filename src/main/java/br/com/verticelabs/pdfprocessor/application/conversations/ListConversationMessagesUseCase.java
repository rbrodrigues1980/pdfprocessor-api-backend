package br.com.verticelabs.pdfprocessor.application.conversations;

import br.com.verticelabs.pdfprocessor.domain.model.ConversationMessage;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationMessageRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListConversationMessagesUseCase {

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;

    public Mono<MessagesResult> execute(String conversationId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(userId -> conversationRepository.findById(conversationId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Conversa não encontrada")))
                        .flatMap(conversation -> {
                            if (conversation.getParticipantUserIds() == null
                                    || !conversation.getParticipantUserIds().contains(userId)) {
                                return Mono.error(new SecurityException("Sem permissão para ver esta conversa"));
                            }
                            return Mono.zip(
                                    messageRepository.findByConversationId(conversationId, safePage, safeSize)
                                            .collectList(),
                                    messageRepository.countByConversationId(conversationId)
                            ).map(tuple -> new MessagesResult(
                                    tuple.getT1(),
                                    tuple.getT2(),
                                    safePage,
                                    safeSize,
                                    conversation.getId(),
                                    conversation.getTitle()));
                        }));
    }

    public record MessagesResult(
            List<ConversationMessage> items,
            long total,
            int page,
            int size,
            String conversationId,
            String title) {
    }
}
