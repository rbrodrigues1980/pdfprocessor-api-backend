package br.com.verticelabs.pdfprocessor.application.conversations;

import br.com.verticelabs.pdfprocessor.domain.model.ConversationMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ReactToConversationMessageUseCase")
class ReactToConversationMessageUseCaseTest {

    @Test
    @DisplayName("adiciona reação e notifica outros")
    void adicionaReacao() {
        ConversationMessage message = ConversationMessage.builder()
                .id("m1")
                .reactions(new ArrayList<>())
                .build();

        var result = ReactToConversationMessageUseCase.applyToggle(message, "u1", "👍");

        assertEquals(1, result.message().getReactions().size());
        assertEquals("👍", result.message().getReactions().get(0).getEmoji());
        assertTrue(result.notifiesOthers());
    }

    @Test
    @DisplayName("mesmo emoji remove a reação sem notificar")
    void toggleRemove() {
        ConversationMessage message = ConversationMessage.builder()
                .id("m1")
                .reactions(new ArrayList<>(List.of(
                        ConversationMessage.Reaction.builder()
                                .emoji("❤️")
                                .userId("u1")
                                .createdAt(Instant.now())
                                .build())))
                .build();

        var result = ReactToConversationMessageUseCase.applyToggle(message, "u1", "❤️");

        assertTrue(result.message().getReactions().isEmpty());
        assertFalse(result.notifiesOthers());
    }

    @Test
    @DisplayName("emoji diferente substitui e notifica")
    void substituiReacao() {
        ConversationMessage message = ConversationMessage.builder()
                .id("m1")
                .reactions(new ArrayList<>(List.of(
                        ConversationMessage.Reaction.builder()
                                .emoji("👍")
                                .userId("u1")
                                .createdAt(Instant.now())
                                .build(),
                        ConversationMessage.Reaction.builder()
                                .emoji("😂")
                                .userId("u2")
                                .createdAt(Instant.now())
                                .build())))
                .build();

        var result = ReactToConversationMessageUseCase.applyToggle(message, "u1", "🙏");

        assertEquals(2, result.message().getReactions().size());
        assertEquals("🙏", result.message().getReactions().stream()
                .filter(r -> "u1".equals(r.getUserId()))
                .findFirst()
                .orElseThrow()
                .getEmoji());
        assertTrue(result.notifiesOthers());
    }

    @Test
    @DisplayName("preview de reação")
    void previewReacao() {
        assertEquals("Maria reagiu com 👍",
                ReactToConversationMessageUseCase.previewReaction("Maria", "👍"));
    }
}
