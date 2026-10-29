package br.com.verticelabs.pdfprocessor.application.notifications;

import br.com.verticelabs.pdfprocessor.domain.model.Notification;
import br.com.verticelabs.pdfprocessor.domain.model.Notification.Attachment;
import br.com.verticelabs.pdfprocessor.domain.model.User;
import br.com.verticelabs.pdfprocessor.domain.repository.NotificationRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.UserRepository;
import br.com.verticelabs.pdfprocessor.domain.service.GridFsService;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateNotificationUseCase {

    public static final int MAX_ATTACHMENTS = 10;
    public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    /** Absorve POSTs duplicados do mesmo clique (mesmo título/corpo/destinatário). */
    private static final int DUPLICATE_WINDOW_SECONDS = 5;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final GridFsService gridFsService;

    public Mono<CreateResult> execute(
            String title,
            String body,
            List<String> recipientUserIds,
            String tenantId,
            boolean allSystem,
            List<FilePart> files) {
        if (title == null || title.isBlank()) {
            return Mono.error(new IllegalArgumentException("Título é obrigatório"));
        }
        if (body == null || body.isBlank()) {
            return Mono.error(new IllegalArgumentException("Mensagem é obrigatória"));
        }
        List<FilePart> fileParts = files != null ? files : List.of();
        if (fileParts.size() > MAX_ATTACHMENTS) {
            return Mono.error(new IllegalArgumentException("Máximo de " + MAX_ATTACHMENTS + " anexos por aviso"));
        }

        return ReactiveSecurityContextHelper.getUserId()
                .zipWith(ReactiveSecurityContextHelper.isSuperAdmin())
                .flatMap(tuple -> {
                    String senderId = tuple.getT1();
                    boolean superAdmin = tuple.getT2();
                    return userRepository.findById(senderId)
                            .switchIfEmpty(Mono.error(new IllegalStateException("Remetente não encontrado")))
                            .flatMap(sender -> storeAttachments(fileParts)
                                    .flatMap(attachments -> resolveRecipients(
                                                    sender, superAdmin, recipientUserIds, tenantId, allSystem)
                                            .filter(r -> !r.getId().equals(senderId))
                                            .collectList()
                                            .flatMap(recipients -> {
                                                if (recipients.isEmpty()) {
                                                    return Mono.error(new IllegalArgumentException(
                                                            "Nenhum destinatário válido encontrado"));
                                                }
                                                String scope = resolveScope(allSystem, tenantId);
                                                Instant now = Instant.now();
                                                String trimmedTitle = title.trim();
                                                String trimmedBody = body.trim();
                                                Instant dedupeWindowStart = now.minusSeconds(DUPLICATE_WINDOW_SECONDS);
                                                return Flux.fromIterable(recipients)
                                                        .concatMap(recipient -> notificationRepository
                                                                .existsRecentDuplicate(
                                                                        sender.getId(),
                                                                        recipient.getId(),
                                                                        trimmedTitle,
                                                                        trimmedBody,
                                                                        dedupeWindowStart)
                                                                .flatMap(exists -> {
                                                                    if (Boolean.TRUE.equals(exists)) {
                                                                        log.info(
                                                                                "⏭️ Aviso duplicado ignorado (janela {}s): recipient={}",
                                                                                DUPLICATE_WINDOW_SECONDS,
                                                                                recipient.getId());
                                                                        return Mono.empty();
                                                                    }
                                                                    return Mono.just(Notification.builder()
                                                                            .title(trimmedTitle)
                                                                            .body(trimmedBody)
                                                                            .senderUserId(sender.getId())
                                                                            .senderNome(sender.getNome())
                                                                            .recipientUserId(recipient.getId())
                                                                            .tenantId(recipient.getTenantId())
                                                                            .scope(scope)
                                                                            .createdAt(now)
                                                                            .readAt(null)
                                                                            .attachments(copyAttachments(attachments))
                                                                            .build());
                                                                }))
                                                        .collectList()
                                                        .flatMap(docs -> {
                                                            if (docs.isEmpty()) {
                                                                // Todos eram duplicatas do mesmo clique — sucesso idempotente
                                                                return Mono.just(new CreateResult(
                                                                        recipients.size(), attachments.size()));
                                                            }
                                                            return notificationRepository.saveAll(docs)
                                                                    .count()
                                                                    .map(count -> new CreateResult(
                                                                            count.intValue(), attachments.size()));
                                                        });
                                            })));
                });
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

    private static List<Attachment> copyAttachments(List<Attachment> source) {
        List<Attachment> copy = new ArrayList<>();
        for (Attachment a : source) {
            copy.add(Attachment.builder()
                    .fileId(a.getFileId())
                    .fileName(a.getFileName())
                    .contentType(a.getContentType())
                    .sizeBytes(a.getSizeBytes())
                    .build());
        }
        return copy;
    }

    private Flux<User> resolveRecipients(
            User sender,
            boolean superAdmin,
            List<String> recipientUserIds,
            String tenantId,
            boolean allSystem) {
        if (allSystem) {
            if (!superAdmin) {
                return Flux.error(new IllegalArgumentException(
                        "Apenas SUPER_ADMIN pode enviar para todos os usuários do sistema"));
            }
            return userRepository.findAllByAtivo(true);
        }
        if (tenantId != null && !tenantId.isBlank()) {
            if (!superAdmin) {
                String senderTenant = sender.getTenantId();
                if (senderTenant == null || !senderTenant.equals(tenantId)) {
                    return Flux.error(new IllegalArgumentException(
                            "Você só pode enviar para o seu próprio tenant"));
                }
            }
            return userRepository.findAllByTenantIdAndAtivo(tenantId, true);
        }
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            return Flux.error(new IllegalArgumentException(
                    "Informe destinatários, um tenant ou allSystem=true"));
        }
        Set<String> uniqueIds = new HashSet<>(recipientUserIds);
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

    private Mono<List<Attachment>> storeAttachments(List<FilePart> files) {
        if (files.isEmpty()) {
            return Mono.just(List.of());
        }
        return Flux.fromIterable(files)
                .concatMap(this::storeOne)
                .collectList();
    }

    private Mono<Attachment> storeOne(FilePart filePart) {
        String fileName = filePart.filename() != null ? filePart.filename() : "anexo";
        String contentType = filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString()
                : "application/octet-stream";
        return DataBufferUtils.join(filePart.content())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    if (bytes.length > MAX_FILE_BYTES) {
                        return Mono.error(new IllegalArgumentException(
                                "Arquivo '" + fileName + "' excede o limite de 20 MB"));
                    }
                    return gridFsService.storeFile(
                                    new ByteArrayInputStream(bytes), fileName, contentType)
                            .map(fileId -> Attachment.builder()
                                    .fileId(fileId)
                                    .fileName(fileName)
                                    .contentType(contentType)
                                    .sizeBytes((long) bytes.length)
                                    .build());
                });
    }

    public record CreateResult(int recipientCount, int attachmentCount) {
    }
}
