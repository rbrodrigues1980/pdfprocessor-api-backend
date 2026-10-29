package br.com.verticelabs.pdfprocessor.application.conversations;

import br.com.verticelabs.pdfprocessor.domain.model.ConversationMessage.Attachment;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationMessageRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationRepository;
import br.com.verticelabs.pdfprocessor.domain.service.GridFsService;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class DownloadConversationAttachmentUseCase {

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final GridFsService gridFsService;

    public Mono<DownloadResult> execute(String conversationId, String messageId, String fileId) {
        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(userId -> conversationRepository.findById(conversationId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Conversa não encontrada")))
                        .flatMap(conversation -> {
                            if (conversation.getParticipantUserIds() == null
                                    || !conversation.getParticipantUserIds().contains(userId)) {
                                return Mono.error(new SecurityException(
                                        "Sem permissão para baixar este anexo"));
                            }
                            return messageRepository.findById(messageId)
                                    .switchIfEmpty(Mono.error(
                                            new IllegalArgumentException("Mensagem não encontrada")))
                                    .flatMap(message -> {
                                        if (!conversationId.equals(message.getConversationId())) {
                                            return Mono.error(new IllegalArgumentException(
                                                    "Mensagem não pertence a esta conversa"));
                                        }
                                        Attachment attachment = findAttachment(message.getAttachments(), fileId);
                                        if (attachment == null) {
                                            return Mono.error(new IllegalArgumentException(
                                                    "Anexo não encontrado"));
                                        }
                                        return gridFsService.retrieveFile(fileId)
                                                .map(stream -> new DownloadResult(
                                                        stream,
                                                        attachment.getFileName(),
                                                        attachment.getContentType() != null
                                                                ? attachment.getContentType()
                                                                : "application/octet-stream",
                                                        attachment.getSizeBytes()));
                                    });
                        }));
    }

    private static Attachment findAttachment(
            java.util.List<Attachment> attachments, String fileId) {
        if (attachments == null) {
            return null;
        }
        return attachments.stream()
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
