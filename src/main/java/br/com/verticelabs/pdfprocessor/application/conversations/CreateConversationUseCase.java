package br.com.verticelabs.pdfprocessor.application.conversations;

import br.com.verticelabs.pdfprocessor.domain.model.Conversation;
import br.com.verticelabs.pdfprocessor.domain.model.ConversationMessage;
import br.com.verticelabs.pdfprocessor.domain.model.ConversationMessage.Attachment;
import br.com.verticelabs.pdfprocessor.domain.model.User;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationMessageRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.UserRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateConversationUseCase {

    private static final int DUPLICATE_WINDOW_SECONDS = 5;

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ConversationAttachmentHelper attachmentHelper;

    public Mono<CreateResult> execute(
            String title,
            String body,
            List<String> recipientUserIds,
            String tenantId,
            boolean allSystem,
            boolean repliesAllowed,
            List<FilePart> files) {
        if (title == null || title.isBlank()) {
            return Mono.error(new IllegalArgumentException("Título é obrigatório"));
        }
        if (body == null || body.isBlank()) {
            return Mono.error(new IllegalArgumentException("Mensagem é obrigatória"));
        }

        return ReactiveSecurityContextHelper.getUserId()
                .zipWith(ReactiveSecurityContextHelper.isSuperAdmin())
                .flatMap(tuple -> {
                    String senderId = tuple.getT1();
                    boolean superAdmin = tuple.getT2();
                    return userRepository.findById(senderId)
                            .switchIfEmpty(Mono.error(new IllegalStateException("Remetente não encontrado")))
                            .flatMap(sender -> attachmentHelper.storeAttachments(files)
                                    .flatMap(attachments -> resolveOtherParticipants(
                                                    sender, superAdmin, recipientUserIds, tenantId, allSystem)
                                            .collectList()
                                            .flatMap(others -> {
                                                if (others.isEmpty()) {
                                                    return Mono.error(new IllegalArgumentException(
                                                            "Nenhum destinatário válido encontrado"));
                                                }
                                                String trimmedTitle = title.trim();
                                                String trimmedBody = body.trim();
                                                String preview = previewOf(trimmedBody);
                                                Instant now = Instant.now();
                                                Instant dedupeStart = now.minusSeconds(DUPLICATE_WINDOW_SECONDS);

                                                return conversationRepository.existsRecentDuplicate(
                                                                sender.getId(), trimmedTitle, preview, dedupeStart)
                                                        .flatMap(exists -> {
                                                            if (Boolean.TRUE.equals(exists)) {
                                                                log.info("⏭️ Conversa duplicada ignorada (janela {}s)",
                                                                        DUPLICATE_WINDOW_SECONDS);
                                                                return conversationRepository
                                                                        .findByParticipantUserId(senderId, false, 0, 1)
                                                                        .filter(c -> trimmedTitle.equals(c.getTitle())
                                                                                && preview.equals(c.getLastMessagePreview())
                                                                                && senderId.equals(c.getCreatedByUserId()))
                                                                        .next()
                                                                        .map(c -> new CreateResult(
                                                                                c.getId(),
                                                                                c.getParticipantUserIds().size(),
                                                                                attachments.size()))
                                                                        .switchIfEmpty(Mono.just(new CreateResult(
                                                                                null,
                                                                                others.size() + 1,
                                                                                attachments.size())));
                                                            }
                                                            return createNew(
                                                                    sender,
                                                                    others,
                                                                    trimmedTitle,
                                                                    trimmedBody,
                                                                    preview,
                                                                    resolveScope(allSystem, tenantId),
                                                                    blankToNull(tenantId),
                                                                    repliesAllowed,
                                                                    attachments,
                                                                    now);
                                                        });
                                            })));
                });
    }

    private Mono<CreateResult> createNew(
            User sender,
            List<User> others,
            String title,
            String body,
            String preview,
            String scope,
            String tenantId,
            boolean repliesAllowed,
            List<Attachment> attachments,
            Instant now) {
        List<String> participantIds = new ArrayList<>();
        participantIds.add(sender.getId());
        for (User u : others) {
            if (!participantIds.contains(u.getId())) {
                participantIds.add(u.getId());
            }
        }

        HashMap<String, Instant> readMap = new HashMap<>();
        readMap.put(sender.getId(), now);

        Conversation conversation = Conversation.builder()
                .title(title)
                .scope(scope)
                .tenantId(tenantId != null ? tenantId : sender.getTenantId())
                .createdByUserId(sender.getId())
                .createdByNome(sender.getNome())
                .createdAt(now)
                .participantUserIds(participantIds)
                .lastMessageAt(now)
                .lastMessagePreview(preview)
                .lastSenderNome(sender.getNome())
                .lastReadAtByUser(readMap)
                .repliesAllowed(repliesAllowed)
                .build();

        return conversationRepository.save(conversation)
                .flatMap(saved -> {
                    ConversationMessage message = ConversationMessage.builder()
                            .conversationId(saved.getId())
                            .senderUserId(sender.getId())
                            .senderNome(sender.getNome())
                            .body(body)
                            .createdAt(now)
                            .attachments(ConversationAttachmentHelper.copyAttachments(attachments))
                            .build();
                    return messageRepository.save(message)
                            .map(msg -> new CreateResult(
                                    saved.getId(),
                                    participantIds.size(),
                                    attachments.size()));
                });
    }

    private Flux<User> resolveOtherParticipants(
            User sender,
            boolean superAdmin,
            List<String> recipientUserIds,
            String tenantId,
            boolean allSystem) {
        String senderId = sender.getId();
        if (allSystem) {
            if (!superAdmin) {
                return Flux.error(new IllegalArgumentException(
                        "Apenas SUPER_ADMIN pode enviar para todos os usuários do sistema"));
            }
            return userRepository.findAllByAtivo(true)
                    .filter(u -> !u.getId().equals(senderId));
        }
        if (tenantId != null && !tenantId.isBlank()) {
            if (!superAdmin) {
                String senderTenant = sender.getTenantId();
                if (senderTenant == null || !senderTenant.equals(tenantId)) {
                    return Flux.error(new IllegalArgumentException(
                            "Você só pode enviar para o seu próprio tenant"));
                }
            }
            return userRepository.findAllByTenantIdAndAtivo(tenantId, true)
                    .filter(u -> !u.getId().equals(senderId));
        }
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            return Flux.error(new IllegalArgumentException(
                    "Informe destinatários, um tenant ou allSystem=true"));
        }
        Set<String> uniqueIds = new HashSet<>(recipientUserIds);
        uniqueIds.remove(senderId);
        return Flux.fromIterable(uniqueIds)
                .flatMap(userRepository::findById)
                .filter(u -> Boolean.TRUE.equals(u.getAtivo()))
                .filter(u -> canSendTo(sender, superAdmin, u));
    }

    private static boolean canSendTo(User sender, boolean superAdmin, User recipient) {
        if (superAdmin) {
            return true;
        }
        String senderTenant = sender.getTenantId();
        if (senderTenant == null) {
            return false;
        }
        return senderTenant.equals(recipient.getTenantId());
    }

    private static String resolveScope(boolean allSystem, String tenantId) {
        if (allSystem) {
            return "SYSTEM";
        }
        if (tenantId != null && !tenantId.isBlank()) {
            return "TENANT";
        }
        return "DIRECT";
    }

    private static String previewOf(String body) {
        String trimmed = body.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 117) + "...";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateResult(String conversationId, int participantCount, int attachmentCount) {
    }
}
