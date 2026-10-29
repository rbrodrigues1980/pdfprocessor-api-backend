package br.com.verticelabs.pdfprocessor.application.notifications;

import br.com.verticelabs.pdfprocessor.domain.model.Notification;
import br.com.verticelabs.pdfprocessor.domain.repository.NotificationRepository;
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

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarkNotificationReadUseCase")
class MarkNotificationReadUseCaseTest {

    @Mock
    NotificationRepository notificationRepository;

    @Test
    void marcaUmaComoLida() {
        MarkNotificationReadUseCase useCase = new MarkNotificationReadUseCase(notificationRepository);
        Notification read = Notification.builder()
                .id("n1")
                .recipientUserId("user-1")
                .title("T")
                .body("B")
                .readAt(Instant.now())
                .build();
        when(notificationRepository.markRead("n1", "user-1")).thenReturn(Mono.just(read));

        var auth = new UsernamePasswordAuthenticationToken(
                "user-1", "n/a", List.of(new SimpleGrantedAuthority("ROLE_TENANT_USER")));

        StepVerifier.create(useCase.markOne("n1")
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .assertNext(n -> assertNotNull(n.getReadAt()))
                .verifyComplete();
    }

    @Test
    void marcaTodas() {
        MarkNotificationReadUseCase useCase = new MarkNotificationReadUseCase(notificationRepository);
        when(notificationRepository.markAllRead("user-1")).thenReturn(Mono.just(3L));

        var auth = new UsernamePasswordAuthenticationToken(
                "user-1", "n/a", List.of(new SimpleGrantedAuthority("ROLE_TENANT_USER")));

        StepVerifier.create(useCase.markAll()
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .expectNext(3L)
                .verifyComplete();
    }
}
