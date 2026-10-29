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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

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
@DisplayName("CreateConversationUseCase")
class CreateConversationUseCaseTest {

    @Mock
    ConversationRepository conversationRepository;
    @Mock
    ConversationMessageRepository messageRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    GridFsService gridFsService;

    CreateConversationUseCase useCase;

    User sender;
    User recipient;

    @BeforeEach
    void setUp() {
        ConversationAttachmentHelper helper = new ConversationAttachmentHelper(gridFsService);
        useCase = new CreateConversationUseCase(
                conversationRepository, messageRepository, userRepository, helper);
        sender = User.builder()
                .id("sender-1")
                .nome("Alice")
                .email("alice@test.com")
                .tenantId("tenant-a")
                .ativo(true)
                .roles(Set.of("TENANT_USER"))
                .build();
        recipient = User.builder()
                .id("recipient-1")
                .nome("Bob")
                .email("bob@test.com")
                .tenantId("tenant-a")
                .ativo(true)
                .roles(Set.of("TENANT_USER"))
                .build();
    }

    @Test
    @DisplayName("cria conversa DIRECT com remetente + destinatário")
    void criaDirect() {
        when(userRepository.findById("sender-1")).thenReturn(Mono.just(sender));
        when(userRepository.findById("recipient-1")).thenReturn(Mono.just(recipient));
        when(conversationRepository.existsRecentDuplicate(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(false));
        when(conversationRepository.save(any())).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId("conv-1");
            return Mono.just(c);
        });
        when(messageRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        Mono<CreateConversationUseCase.CreateResult> result = useCase
                .execute("Olá", "Primeira mensagem", List.of("recipient-1"), null, false, List.of())
                .contextWrite(securityContext("sender-1", "TENANT_USER"));

        StepVerifier.create(result)
                .assertNext(r -> {
                    assertEquals("conv-1", r.conversationId());
                    assertEquals(2, r.participantCount());
                })
                .verifyComplete();

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        Conversation saved = captor.getValue();
        assertEquals("DIRECT", saved.getScope());
        assertTrue(saved.getParticipantUserIds().contains("sender-1"));
        assertTrue(saved.getParticipantUserIds().contains("recipient-1"));
    }

    @Test
    @DisplayName("allSystem negado para não SUPER_ADMIN")
    void allSystemNegado() {
        when(userRepository.findById("sender-1")).thenReturn(Mono.just(sender));

        Mono<CreateConversationUseCase.CreateResult> result = useCase
                .execute("Broadcast", "Msg", null, null, true, List.of())
                .contextWrite(securityContext("sender-1", "TENANT_USER"));

        StepVerifier.create(result)
                .expectErrorMatches(e -> e instanceof IllegalArgumentException
                        && e.getMessage().contains("SUPER_ADMIN"))
                .verify();

        verify(conversationRepository, never()).save(any());
    }

    @Test
    @DisplayName("SUPER_ADMIN cria SYSTEM com snapshot de participantes")
    void allSystemOk() {
        User admin = User.builder()
                .id("admin-1")
                .nome("Admin")
                .email("admin@test.com")
                .ativo(true)
                .roles(Set.of("SUPER_ADMIN"))
                .build();
        when(userRepository.findById("admin-1")).thenReturn(Mono.just(admin));
        when(userRepository.findAllByAtivo(true)).thenReturn(Flux.just(admin, recipient, sender));
        when(conversationRepository.existsRecentDuplicate(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(false));
        when(conversationRepository.save(any())).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId("conv-sys");
            return Mono.just(c);
        });
        when(messageRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        Mono<CreateConversationUseCase.CreateResult> result = useCase
                .execute("Sistema", "Aviso geral", null, null, true, List.of())
                .contextWrite(securityContext("admin-1", "SUPER_ADMIN"));

        StepVerifier.create(result)
                .assertNext(r -> assertEquals(3, r.participantCount())) // admin + 2
                .verifyComplete();

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertEquals("SYSTEM", captor.getValue().getScope());
    }

    @Test
    @DisplayName("TENANT inclui ativos do tenant sem o remetente duplicado")
    void tenantOk() {
        when(userRepository.findById("sender-1")).thenReturn(Mono.just(sender));
        when(userRepository.findAllByTenantIdAndAtivo("tenant-a", true))
                .thenReturn(Flux.just(sender, recipient));
        when(conversationRepository.existsRecentDuplicate(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(false));
        when(conversationRepository.save(any())).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId("conv-t");
            return Mono.just(c);
        });
        when(messageRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase
                        .execute("Time", "Oi time", null, "tenant-a", false, List.of())
                        .contextWrite(securityContext("sender-1", "TENANT_USER")))
                .assertNext(r -> assertEquals(2, r.participantCount()))
                .verifyComplete();

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertEquals("TENANT", captor.getValue().getScope());
    }

    @Test
    @DisplayName("dedupe retorna sem salvar de novo")
    void dedupe() {
        when(userRepository.findById("sender-1")).thenReturn(Mono.just(sender));
        when(userRepository.findById("recipient-1")).thenReturn(Mono.just(recipient));
        when(conversationRepository.existsRecentDuplicate(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(true));
        Conversation existing = Conversation.builder()
                .id("existing")
                .title("Olá")
                .createdByUserId("sender-1")
                .lastMessagePreview("Primeira mensagem")
                .participantUserIds(List.of("sender-1", "recipient-1"))
                .build();
        when(conversationRepository.findByParticipantUserId("sender-1", false, 0, 1))
                .thenReturn(Flux.just(existing));

        StepVerifier.create(useCase
                        .execute("Olá", "Primeira mensagem", List.of("recipient-1"), null, false, List.of())
                        .contextWrite(securityContext("sender-1", "TENANT_USER")))
                .assertNext(r -> assertEquals("existing", r.conversationId()))
                .verifyComplete();

        verify(conversationRepository, never()).save(any());
        verify(messageRepository, never()).save(any(ConversationMessage.class));
    }

    private static Context securityContext(String userId, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId, "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        return ReactiveSecurityContextHolder.withAuthentication(auth);
    }
}
