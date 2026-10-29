package br.com.verticelabs.pdfprocessor.application.notifications;

import br.com.verticelabs.pdfprocessor.domain.model.Notification;
import br.com.verticelabs.pdfprocessor.domain.model.User;
import br.com.verticelabs.pdfprocessor.domain.repository.NotificationRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.UserRepository;
import br.com.verticelabs.pdfprocessor.domain.service.GridFsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.multipart.FilePart;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateNotificationUseCase")
class CreateNotificationUseCaseTest {

    @Mock
    NotificationRepository notificationRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    GridFsService gridFsService;

    CreateNotificationUseCase useCase;

    User sender;
    User recipient;

    @BeforeEach
    void setUp() {
        useCase = new CreateNotificationUseCase(notificationRepository, userRepository, gridFsService);
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

    @AfterEach
    void tearDown() {
        // no-op
    }

    @Test
    @DisplayName("envia aviso individual no mesmo tenant")
    void enviaIndividual() {
        when(userRepository.findById("sender-1")).thenReturn(Mono.just(sender));
        when(userRepository.findById("recipient-1")).thenReturn(Mono.just(recipient));
        when(notificationRepository.existsRecentDuplicate(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(false));
        when(notificationRepository.saveAll(any())).thenAnswer(inv -> {
            Iterable<Notification> docs = inv.getArgument(0);
            return Flux.fromIterable(docs);
        });

        Mono<CreateNotificationUseCase.CreateResult> result = useCase
                .execute("Olá", "Mensagem", List.of("recipient-1"), null, false, List.of())
                .contextWrite(securityContext("sender-1", "TENANT_USER"));

        StepVerifier.create(result)
                .assertNext(r -> {
                    assertEquals(1, r.recipientCount());
                    assertEquals(0, r.attachmentCount());
                })
                .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Notification>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(notificationRepository).saveAll(captor.capture());
        Notification saved = captor.getValue().iterator().next();
        assertEquals("recipient-1", saved.getRecipientUserId());
        assertEquals("DIRECT", saved.getScope());
    }

    @Test
    @DisplayName("allSystem negado para não SUPER_ADMIN")
    void allSystemNegado() {
        when(userRepository.findById("sender-1")).thenReturn(Mono.just(sender));

        Mono<CreateNotificationUseCase.CreateResult> result = useCase
                .execute("Broadcast", "Msg", null, null, true, List.of())
                .contextWrite(securityContext("sender-1", "TENANT_USER"));

        StepVerifier.create(result)
                .expectErrorMatches(e -> e instanceof IllegalArgumentException
                        && e.getMessage().contains("SUPER_ADMIN"))
                .verify();

        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("SUPER_ADMIN envia para todos ativos do sistema")
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
        when(notificationRepository.existsRecentDuplicate(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(false));
        when(notificationRepository.saveAll(any())).thenAnswer(inv -> Flux.fromIterable(inv.getArgument(0)));

        Mono<CreateNotificationUseCase.CreateResult> result = useCase
                .execute("Sistema", "Aviso geral", null, null, true, List.of())
                .contextWrite(securityContext("admin-1", "SUPER_ADMIN"));

        StepVerifier.create(result)
                .assertNext(r -> assertEquals(2, r.recipientCount())) // exclui o próprio admin
                .verifyComplete();
    }

    @Test
    @DisplayName("anexa arquivo no GridFS e replica metadados")
    void comAnexo() {
        when(userRepository.findById("sender-1")).thenReturn(Mono.just(sender));
        when(userRepository.findById("recipient-1")).thenReturn(Mono.just(recipient));
        when(notificationRepository.existsRecentDuplicate(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(false));
        when(gridFsService.storeFile(any(), anyString(), anyString())).thenReturn(Mono.just("file-xyz"));
        when(notificationRepository.saveAll(any())).thenAnswer(inv -> Flux.fromIterable(inv.getArgument(0)));

        FilePart filePart = mock(FilePart.class);
        when(filePart.filename()).thenReturn("doc.pdf");
        when(filePart.headers()).thenReturn(new HttpHeaders());
        when(filePart.content()).thenReturn(Flux.just(
                org.springframework.core.io.buffer.DefaultDataBufferFactory.sharedInstance.wrap(
                        "conteudo".getBytes())));

        Mono<CreateNotificationUseCase.CreateResult> result = useCase
                .execute("Com anexo", "Veja o PDF", List.of("recipient-1"), null, false, List.of(filePart))
                .contextWrite(securityContext("sender-1", "TENANT_USER"));

        StepVerifier.create(result)
                .assertNext(r -> {
                    assertEquals(1, r.recipientCount());
                    assertEquals(1, r.attachmentCount());
                })
                .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Notification>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(notificationRepository).saveAll(captor.capture());
        Notification saved = captor.getValue().iterator().next();
        assertEquals(1, saved.getAttachments().size());
        assertEquals("file-xyz", saved.getAttachments().get(0).getFileId());
        assertEquals("doc.pdf", saved.getAttachments().get(0).getFileName());
    }

    private static Context securityContext(String userId, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId, "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        return ReactiveSecurityContextHolder.withAuthentication(auth);
    }
}
