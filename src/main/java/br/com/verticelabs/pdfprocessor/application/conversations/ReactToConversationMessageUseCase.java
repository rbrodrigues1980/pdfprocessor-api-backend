package br.com.verticelabs.pdfprocessor.application.conversations;

import br.com.verticelabs.pdfprocessor.domain.model.ConversationMessage;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationMessageRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.UserRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final UserRepository userRepository;

    /**
     * Toggle: mesmo emoji remove; emoji diferente substitui a reação do usuário.
     * Ao adicionar/trocar reação, atualiza a conversa para notificar no sininho.
     */
    public Mono<ConversationMessage> execute(String conversationId, String messageId, String emoji) {
        if (emoji == null || emoji.isBlank() || !ALLOWED_EMOJIS.contains(emoji.trim())) {
            return Mono.error(new IllegalArgumentException(
                    "Emoji inválido. Use um dos básicos: " + String.join(" ", ALLOWED_EMOJIS)));
        }
        String normalized = emoji.trim();

        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(userId -> userRepository.findById(userId)
                        .switchIfEmpty(Mono.error(new IllegalStateException("Usuário não encontrado")))
                        .flatMap(reactor -> conversationRepository.findById(conversationId)
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
                                                ToggleResult toggle = applyToggle(message, userId, normalized);
                                                return messageRepository.save(toggle.message())
                                                        .flatMap(saved -> {
                                                            if (!toggle.notifiesOthers()) {
                                                                return Mono.just(saved);
                                                            }
                                                            Instant now = Instant.now();
                                                            conversation.setLastMessageAt(now);
                                                            conversation.setLastMessagePreview(
                                                                    previewReaction(reactor.getNome(), normalized));
                                                            conversation.setLastSenderNome(reactor.getNome());
                                                            HashMap<String, Instant> reads =
                                                                    conversation.getLastReadAtByUser() != null
                                                                            ? new HashMap<>(conversation.getLastReadAtByUser())
                                                                            : new HashMap<>();
                                                            reads.put(userId, now);
                                                            conversation.setLastReadAtByUser(reads);
                                                            return conversationRepository.save(conversation)
                                                                    .thenReturn(saved);
                                                        });
                                            });
                                })));
    }

    static String previewReaction(String nome, String emoji) {
        String who = nome != null && !nome.isBlank() ? nome.trim() : "Alguém";
        String preview = who + " reagiu com " + emoji;
        return preview.length() <= 120 ? preview : preview.substring(0, 117) + "...";
    }

    static ToggleResult applyToggle(ConversationMessage message, String userId, String emoji) {
        List<ConversationMessage.Reaction> reactions = message.getReactions() != null
                ? new ArrayList<>(message.getReactions())
                : new ArrayList<>();

        ConversationMessage.Reaction existing = reactions.stream()
                .filter(r -> userId.equals(r.getUserId()))
                .findFirst()
                .orElse(null);

        ToggleAction action;
        if (existing != null && emoji.equals(existing.getEmoji())) {
            reactions.removeIf(r -> userId.equals(r.getUserId()));
            action = ToggleAction.REMOVED;
        } else {
            reactions.removeIf(r -> userId.equals(r.getUserId()));
            reactions.add(ConversationMessage.Reaction.builder()
                    .emoji(emoji)
                    .userId(userId)
                    .createdAt(Instant.now())
                    .build());
            action = existing == null ? ToggleAction.ADDED : ToggleAction.CHANGED;
        }

        message.setReactions(reactions);
        return new ToggleResult(message, action);
    }

    enum ToggleAction {
        ADDED,
        CHANGED,
        REMOVED;

        boolean notifiesOthers() {
            return this == ADDED || this == CHANGED;
        }
    }

    record ToggleResult(ConversationMessage message, ToggleAction action) {
        boolean notifiesOthers() {
            return action.notifiesOthers();
        }
    }
}
