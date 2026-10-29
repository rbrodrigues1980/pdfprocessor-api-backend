package br.com.verticelabs.pdfprocessor.application.notifications;

import br.com.verticelabs.pdfprocessor.domain.model.Notification;
import br.com.verticelabs.pdfprocessor.domain.repository.NotificationRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MarkNotificationReadUseCase {

    private final NotificationRepository notificationRepository;

    public Mono<Notification> markOne(String notificationId) {
        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(userId -> notificationRepository.markRead(notificationId, userId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                "Notificação não encontrada ou sem permissão"))));
    }

    public Mono<Long> markAll() {
        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(notificationRepository::markAllRead);
    }
}
