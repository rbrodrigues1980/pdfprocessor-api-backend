package br.com.verticelabs.pdfprocessor.application.conversations;

import br.com.verticelabs.pdfprocessor.domain.model.ConversationMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ReactToConversationMessageUseCase")
class ReactToConversationMessageUseCaseTest {

    @Test
    @DisplayName("adiciona reação quando usuário ainda não reagiu")
    void adicionaReacao() {
        ConversationMessage message = ConversationMessage.builder()
                .id("m1")
                .reactions(new ArrayList<>())
                .build();

        ReactToConversationMessageUseCase.applyToggle(message, "u1", "👍");

        assertEquals(1, message.getReactions().size());
        assertEquals("👍", message.getReactions().get(0).getEmoji());
        assertEquals("u1", message.getReactions().get(0).getUserId());
    }

    @Test
    @DisplayName("mesmo emoji remove a reação (toggle)")
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

        ReactToConversationMessageUseCase.applyToggle(message, "u1", "❤️");

        assertTrue(message.getReactions().isEmpty());
    }

    @Test
    @DisplayName("emoji diferente substitui a reação do usuário")
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

        ReactToConversationMessageUseCase.applyToggle(message, "u1", "🙏");

        assertEquals(2, message.getReactions().size());
        assertEquals(1, message.getReactions().stream().filter(r -> "u1".equals(r.getUserId())).count());
        assertEquals("🙏", message.getReactions().stream()
                .filter(r -> "u1".equals(r.getUserId()))
                .findFirst()
                .orElseThrow()
                .getEmoji());
    }
}
