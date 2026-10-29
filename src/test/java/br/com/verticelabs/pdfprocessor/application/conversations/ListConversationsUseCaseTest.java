package br.com.verticelabs.pdfprocessor.application.conversations;

import br.com.verticelabs.pdfprocessor.domain.model.Conversation;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListConversationsUseCase")
class ListConversationsUseCaseTest {

    @Mock
    ConversationRepository conversationRepository;

    @InjectMocks
    ListConversationsUseCase useCase;

    @Test
    @DisplayName("marca unread corretamente e filtra unreadOnly")
    void unreadFilter() {
        Instant now = Instant.parse("2026-10-28T12:00:00Z");
        Conversation unread = Conversation.builder()
                .id("c1")
                .title("A")
                .participantUserIds(List.of("u1", "u2"))
                .lastMessageAt(now)
                .lastReadAtByUser(new HashMap<>())
                .build();
        Conversation read = Conversation.builder()
                .id("c2")
                .title("B")
                .participantUserIds(List.of("u1", "u2"))
                .lastMessageAt(now.minusSeconds(60))
                .lastReadAtByUser(Map.of("u1", now))
                .build();

        when(conversationRepository.findByParticipantUserId(eq("u1"), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(Flux.just(unread, read));

        StepVerifier.create(useCase.execute(true, 0, 20)
                        .contextWrite(securityContext("u1")))
                .assertNext(result -> {
                    assertEquals(1, result.items().size());
                    assertEquals("c1", result.items().get(0).getId());
                    assertEquals(1, result.unreadCount());
                    assertTrue(result.items().get(0).isUnreadFor("u1"));
                })
                .verifyComplete();
    }

    private static Context securityContext(String userId) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId, "n/a", List.of(new SimpleGrantedAuthority("ROLE_TENANT_USER")));
        return ReactiveSecurityContextHolder.withAuthentication(auth);
    }
}
