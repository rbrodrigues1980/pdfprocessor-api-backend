package br.com.verticelabs.pdfprocessor.application.conversations;

import br.com.verticelabs.pdfprocessor.domain.model.ConversationMessage;
import br.com.verticelabs.pdfprocessor.domain.model.ConversationMessage.Attachment;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationMessageRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.UserRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReplyConversationUseCase {

    private static final int DUPLICATE_WINDOW_SECONDS = 5;

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ConversationAttachmentHelper attachmentHelper;

    public Mono<ConversationMessage> execute(String conversationId, String body, List<FilePart> files) {
        if (body == null || body.isBlank()) {
            return Mono.error(new IllegalArgumentException("Mensagem é obrigatória"));
        }
        String trimmedBody = body.trim();

        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(userId -> userRepository.findById(userId)
                        .switchIfEmpty(Mono.error(new IllegalStateException("Usuário não encontrado")))
                        .flatMap(sender -> conversationRepository.findById(conversationId)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Conversa não encontrada")))
                                .flatMap(conversation -> {
                                    if (conversation.getParticipantUserIds() == null
                                            || !conversation.getParticipantUserIds().contains(userId)) {
                                        return Mono.error(new SecurityException(
                                                "Sem permissão para responder nesta conversa"));
                                    }
                                    boolean isCreator = userId.equals(conversation.getCreatedByUserId());
                                    if (!conversation.areRepliesAllowed() && !isCreator) {
                                        return Mono.error(new SecurityException(
                                                "Este aviso não permite respostas"));
                                    }
                                    Instant now = Instant.now();
                                    Instant dedupeStart = now.minusSeconds(DUPLICATE_WINDOW_SECONDS);
                                    return messageRepository.existsRecentDuplicate(
                                                    conversationId, userId, trimmedBody, dedupeStart)
                                            .flatMap(exists -> {
                                                if (Boolean.TRUE.equals(exists)) {
                                                    log.info("⏭️ Resposta duplicada ignorada na conversa {}",
                                                            conversationId);
                                                    return messageRepository
                                                            .findByConversationId(conversationId, 0, 500)
                                                            .filter(m -> userId.equals(m.getSenderUserId())
                                                                    && trimmedBody.equals(m.getBody()))
                                                            .reduce((a, b) -> b); // última
                                                }
                                                return attachmentHelper.storeAttachments(files)
                                                        .flatMap(attachments -> saveReply(
                                                                conversation.getId(),
                                                                sender.getId(),
                                                                sender.getNome(),
                                                                trimmedBody,
                                                                attachments,
                                                                now));
                                            });
                                })));
    }

    private Mono<ConversationMessage> saveReply(
            String conversationId,
            String senderId,
            String senderNome,
            String body,
            List<Attachment> attachments,
            Instant now) {
        ConversationMessage message = ConversationMessage.builder()
                .conversationId(conversationId)
                .senderUserId(senderId)
                .senderNome(senderNome)
                .body(body)
                .createdAt(now)
                .attachments(ConversationAttachmentHelper.copyAttachments(attachments))
                .build();

        return messageRepository.save(message)
                        .flatMap(saved -> conversationRepository.findById(conversationId)
                        .flatMap(c -> {
                            c.setLastMessageAt(now);
                            c.setLastMessagePreview(previewOf(body));
                            c.setLastSenderNome(senderNome);
                            HashMap<String, Instant> reads = c.getLastReadAtByUser() != null
                                    ? new HashMap<>(c.getLastReadAtByUser())
                                    : new HashMap<>();
                            reads.put(senderId, now);
                            c.setLastReadAtByUser(reads);
                            return conversationRepository.save(c).thenReturn(saved);
                        }));
    }

    private static String previewOf(String body) {
        String trimmed = body.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 117) + "...";
    }
}
