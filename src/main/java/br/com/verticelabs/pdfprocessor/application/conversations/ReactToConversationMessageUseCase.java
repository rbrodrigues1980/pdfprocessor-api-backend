package br.com.verticelabs.pdfprocessor.application.conversations;

import br.com.verticelabs.pdfprocessor.domain.model.ConversationMessage;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationMessageRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReactToConversationMessageUseCase {

    /** Emojis básicos (mesmo conjunto do WhatsApp). */
    public static final Set<String> ALLOWED_EMOJIS = new LinkedHashSet<>(List.of(
            "👍", "❤️", "😂", "😮", "😢", "🙏"));

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;

    /**
     * Toggle: mesmo emoji remove; emoji diferente substitui a reação do usuário.
     */
    public Mono<ConversationMessage> execute(String conversationId, String messageId, String emoji) {
        if (emoji == null || emoji.isBlank() || !ALLOWED_EMOJIS.contains(emoji.trim())) {
            return Mono.error(new IllegalArgumentException(
                    "Emoji inválido. Use um dos básicos: " + String.join(" ", ALLOWED_EMOJIS)));
        }
        String normalized = emoji.trim();

        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(userId -> conversationRepository.findById(conversationId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Conversa não encontrada")))
                        .flatMap(conversation -> {
                            if (conversation.getParticipantUserIds() == null
                                    || !conversation.getParticipantUserIds().contains(userId)) {
                                return Mono.error(new SecurityException(
                                        "Sem permissão para reagir nesta conversa"));
                            }
                            return messageRepository.findById(messageId)
                                    .switchIfEmpty(Mono.error(
                                            new IllegalArgumentException("Mensagem não encontrada")))
                                    .flatMap(message -> {
                                        if (!conversationId.equals(message.getConversationId())) {
                                            return Mono.error(new IllegalArgumentException(
                                                    "Mensagem não pertence a esta conversa"));
                                        }
                                        return messageRepository.save(applyToggle(message, userId, normalized));
                                    });
                        }));
    }

    static ConversationMessage applyToggle(ConversationMessage message, String userId, String emoji) {
        List<ConversationMessage.Reaction> reactions = message.getReactions() != null
                ? new ArrayList<>(message.getReactions())
                : new ArrayList<>();

        ConversationMessage.Reaction existing = reactions.stream()
                .filter(r -> userId.equals(r.getUserId()))
                .findFirst()
                .orElse(null);

        if (existing != null && emoji.equals(existing.getEmoji())) {
            reactions.removeIf(r -> userId.equals(r.getUserId()));
        } else {
            reactions.removeIf(r -> userId.equals(r.getUserId()));
            reactions.add(ConversationMessage.Reaction.builder()
                    .emoji(emoji)
                    .userId(userId)
                    .createdAt(Instant.now())
                    .build());
        }

        message.setReactions(reactions);
        return message;
    }
}
