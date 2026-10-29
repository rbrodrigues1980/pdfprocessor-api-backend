package br.com.verticelabs.pdfprocessor.application.notifications;

import br.com.verticelabs.pdfprocessor.domain.model.Notification;
import br.com.verticelabs.pdfprocessor.domain.model.Notification.Attachment;
import br.com.verticelabs.pdfprocessor.domain.repository.NotificationRepository;
import br.com.verticelabs.pdfprocessor.domain.service.GridFsService;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class DownloadNotificationAttachmentUseCase {

    private final NotificationRepository notificationRepository;
    private final GridFsService gridFsService;

    public Mono<DownloadResult> execute(String notificationId, String fileId) {
        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(userId -> notificationRepository.findById(notificationId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Notificação não encontrada")))
                        .flatMap(notification -> {
                            if (!userId.equals(notification.getRecipientUserId())
                                    && !userId.equals(notification.getSenderUserId())) {
                                return Mono.error(new IllegalArgumentException(
                                        "Sem permissão para baixar este anexo"));
                            }
                            Attachment attachment = findAttachment(notification, fileId);
                            if (attachment == null) {
                                return Mono.error(new IllegalArgumentException("Anexo não encontrado"));
                            }
                            return gridFsService.retrieveFile(fileId)
                                    .map(stream -> new DownloadResult(
                                            stream,
                                            attachment.getFileName(),
                                            attachment.getContentType() != null
                                                    ? attachment.getContentType()
                                                    : "application/octet-stream",
                                            attachment.getSizeBytes()));
                        }));
    }

    private static Attachment findAttachment(Notification notification, String fileId) {
        if (notification.getAttachments() == null) {
            return null;
        }
        return notification.getAttachments().stream()
                .filter(a -> fileId.equals(a.getFileId()))
                .findFirst()
                .orElse(null);
    }

    public record DownloadResult(
            InputStream inputStream,
            String fileName,
            String contentType,
            Long sizeBytes) {
    }
}
