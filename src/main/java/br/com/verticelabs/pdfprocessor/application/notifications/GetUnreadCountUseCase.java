package br.com.verticelabs.pdfprocessor.application.notifications;

import br.com.verticelabs.pdfprocessor.domain.repository.NotificationRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class GetUnreadCountUseCase {

    private final NotificationRepository notificationRepository;

    public Mono<Long> execute() {
        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(notificationRepository::countUnreadByRecipientUserId);
    }
}
