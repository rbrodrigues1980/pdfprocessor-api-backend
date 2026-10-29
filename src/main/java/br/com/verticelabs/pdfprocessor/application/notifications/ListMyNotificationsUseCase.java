package br.com.verticelabs.pdfprocessor.application.notifications;

import br.com.verticelabs.pdfprocessor.domain.model.Notification;
import br.com.verticelabs.pdfprocessor.domain.repository.NotificationRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListMyNotificationsUseCase {

    private final NotificationRepository notificationRepository;

    public Mono<InboxResult> execute(boolean unreadOnly, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(userId -> Mono.zip(
                        notificationRepository.findByRecipientUserId(userId, unreadOnly, safePage, safeSize)
                                .collectList(),
                        notificationRepository.countByRecipientUserId(userId, unreadOnly),
                        notificationRepository.countUnreadByRecipientUserId(userId)
                ).map(tuple -> new InboxResult(tuple.getT1(), tuple.getT2(), tuple.getT3(), safePage, safeSize)));
    }

    public record InboxResult(
            List<Notification> items,
            long total,
            long unreadCount,
            int page,
            int size) {
    }
}
