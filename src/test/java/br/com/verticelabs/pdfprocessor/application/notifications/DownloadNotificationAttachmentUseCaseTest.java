package br.com.verticelabs.pdfprocessor.application.notifications;

import br.com.verticelabs.pdfprocessor.domain.model.Notification;
import br.com.verticelabs.pdfprocessor.domain.model.Notification.Attachment;
import br.com.verticelabs.pdfprocessor.domain.repository.NotificationRepository;
import br.com.verticelabs.pdfprocessor.domain.service.GridFsService;
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

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DownloadNotificationAttachmentUseCase")
class DownloadNotificationAttachmentUseCaseTest {

    @Mock
    NotificationRepository notificationRepository;
    @Mock
    GridFsService gridFsService;

    @Test
    void destinatarioPodeBaixar() {
        var useCase = new DownloadNotificationAttachmentUseCase(notificationRepository, gridFsService);
        Notification n = Notification.builder()
                .id("n1")
                .recipientUserId("user-1")
                .senderUserId("user-2")
                .attachments(List.of(Attachment.builder()
                        .fileId("f1")
                        .fileName("a.pdf")
                        .contentType("application/pdf")
                        .sizeBytes(10L)
                        .build()))
                .build();
        when(notificationRepository.findById("n1")).thenReturn(Mono.just(n));
        when(gridFsService.retrieveFile("f1"))
                .thenReturn(Mono.just(new ByteArrayInputStream(new byte[]{1, 2, 3})));

        var auth = new UsernamePasswordAuthenticationToken(
                "user-1", "n/a", List.of(new SimpleGrantedAuthority("ROLE_TENANT_USER")));

        StepVerifier.create(useCase.execute("n1", "f1")
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .assertNext(r -> assertEquals("a.pdf", r.fileName()))
                .verifyComplete();
    }

    @Test
    void terceiroNaoPodeBaixar() {
        var useCase = new DownloadNotificationAttachmentUseCase(notificationRepository, gridFsService);
        Notification n = Notification.builder()
                .id("n1")
                .recipientUserId("user-1")
                .senderUserId("user-2")
                .attachments(List.of(Attachment.builder()
                        .fileId("f1")
                        .fileName("a.pdf")
                        .contentType("application/pdf")
                        .sizeBytes(10L)
                        .build()))
                .build();
        when(notificationRepository.findById("n1")).thenReturn(Mono.just(n));

        var auth = new UsernamePasswordAuthenticationToken(
                "user-3", "n/a", List.of(new SimpleGrantedAuthority("ROLE_TENANT_USER")));

        StepVerifier.create(useCase.execute("n1", "f1")
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .expectErrorMatches(e -> e.getMessage() != null && e.getMessage().contains("permissão"))
                .verify();
    }
}
