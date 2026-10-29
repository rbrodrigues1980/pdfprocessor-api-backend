package br.com.verticelabs.pdfprocessor.application.conversations;

import br.com.verticelabs.pdfprocessor.domain.model.Conversation;
import br.com.verticelabs.pdfprocessor.domain.model.ConversationMessage;
import br.com.verticelabs.pdfprocessor.domain.model.User;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationMessageRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.UserRepository;
import br.com.verticelabs.pdfprocessor.domain.service.GridFsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReplyConversationUseCase")
class ReplyConversationUseCaseTest {

    @Mock
    ConversationRepository conversationRepository;
    @Mock
    ConversationMessageRepository messageRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    GridFsService gridFsService;

    ReplyConversationUseCase useCase;

    User bob;
    Conversation conversation;

    @BeforeEach
    void setUp() {
        useCase = new ReplyConversationUseCase(
                conversationRepository,
                messageRepository,
                userRepository,
                new ConversationAttachmentHelper(gridFsService));
        bob = User.builder()
                .id("recipient-1")
                .nome("Bob")
                .email("bob@test.com")
                .tenantId("tenant-a")
                .ativo(true)
                .roles(Set.of("TENANT_USER"))
                .build();
        conversation = Conversation.builder()
                .id("conv-1")
                .title("Olá")
                .participantUserIds(new ArrayList<>(List.of("sender-1", "recipient-1")))
                .lastReadAtByUser(new HashMap<>())
                .lastMessageAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("participante pode responder e atualiza lastMessage")
    void replyOk() {
        when(userRepository.findById("recipient-1")).thenReturn(Mono.just(bob));
        when(conversationRepository.findById("conv-1")).thenReturn(Mono.just(conversation));
        when(messageRepository.existsRecentDuplicate(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(false));
        when(messageRepository.save(any())).thenAnswer(inv -> {
            ConversationMessage m = inv.getArgument(0);
            m.setId("msg-2");
            return Mono.just(m);
        });
        when(conversationRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute("conv-1", "Resposta do Bob", List.of())
                        .contextWrite(securityContext("recipient-1")))
                .assertNext(m -> {
                    assertEquals("msg-2", m.getId());
                    assertEquals("Resposta do Bob", m.getBody());
                    assertEquals("recipient-1", m.getSenderUserId());
                })
                .verifyComplete();

        verify(conversationRepository).save(any());
        assertTrue(conversation.isUnreadFor("sender-1"));
    }

    @Test
    @DisplayName("não participante recebe SecurityException")
    void replyForbidden() {
        User outsider = User.builder()
                .id("outsider")
                .nome("Out")
                .ativo(true)
                .roles(Set.of("TENANT_USER"))
                .build();
        when(userRepository.findById("outsider")).thenReturn(Mono.just(outsider));
        when(conversationRepository.findById("conv-1")).thenReturn(Mono.just(conversation));

        StepVerifier.create(useCase.execute("conv-1", "Hack", List.of())
                        .contextWrite(securityContext("outsider")))
                .expectError(SecurityException.class)
                .verify();

        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("aviso com repliesAllowed=false rejeita resposta de destinatário")
    void replyBlockedOnNotice() {
        conversation.setRepliesAllowed(false);
        conversation.setCreatedByUserId("sender-1");
        when(userRepository.findById("recipient-1")).thenReturn(Mono.just(bob));
        when(conversationRepository.findById("conv-1")).thenReturn(Mono.just(conversation));

        StepVerifier.create(useCase.execute("conv-1", "Tentativa", List.of())
                        .contextWrite(securityContext("recipient-1")))
                .expectErrorMatches(e -> e instanceof SecurityException
                        && e.getMessage().contains("não permite respostas"))
                .verify();

        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("autor pode seguir postando mesmo com repliesAllowed=false")
    void creatorCanPostOnNotice() {
        conversation.setRepliesAllowed(false);
        conversation.setCreatedByUserId("sender-1");
        User alice = User.builder()
                .id("sender-1")
                .nome("Alice")
                .email("alice@test.com")
                .tenantId("tenant-a")
                .ativo(true)
                .roles(Set.of("TENANT_USER"))
                .build();
        when(userRepository.findById("sender-1")).thenReturn(Mono.just(alice));
        when(conversationRepository.findById("conv-1")).thenReturn(Mono.just(conversation));
        when(messageRepository.existsRecentDuplicate(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(false));
        when(messageRepository.save(any())).thenAnswer(inv -> {
            ConversationMessage m = inv.getArgument(0);
            m.setId("msg-author");
            return Mono.just(m);
        });
        when(conversationRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute("conv-1", "Atualização do aviso", List.of())
                        .contextWrite(securityContext("sender-1")))
                .assertNext(m -> {
                    assertEquals("msg-author", m.getId());
                    assertEquals("sender-1", m.getSenderUserId());
                })
                .verifyComplete();
    }

    private static Context securityContext(String userId) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId, "n/a", List.of(new SimpleGrantedAuthority("ROLE_TENANT_USER")));
        return ReactiveSecurityContextHolder.withAuthentication(auth);
    }
}
