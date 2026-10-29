package br.com.verticelabs.pdfprocessor.interfaces.notifications;

import br.com.verticelabs.pdfprocessor.application.notifications.CreateNotificationUseCase;
import br.com.verticelabs.pdfprocessor.application.notifications.DownloadNotificationAttachmentUseCase;
import br.com.verticelabs.pdfprocessor.application.notifications.GetUnreadCountUseCase;
import br.com.verticelabs.pdfprocessor.application.notifications.ListMyNotificationsUseCase;
import br.com.verticelabs.pdfprocessor.application.notifications.MarkNotificationReadUseCase;
import br.com.verticelabs.pdfprocessor.interfaces.notifications.dto.CreateNotificationResponse;
import br.com.verticelabs.pdfprocessor.interfaces.notifications.dto.NotificationListResponse;
import br.com.verticelabs.pdfprocessor.interfaces.notifications.dto.UnreadCountResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final CreateNotificationUseCase createNotificationUseCase;
    private final ListMyNotificationsUseCase listMyNotificationsUseCase;
    private final GetUnreadCountUseCase getUnreadCountUseCase;
    private final MarkNotificationReadUseCase markNotificationReadUseCase;
    private final DownloadNotificationAttachmentUseCase downloadNotificationAttachmentUseCase;
    private final NotificationMapper notificationMapper;
    private final ObjectMapper objectMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Object>> createNotification(
            @RequestPart("title") String title,
            @RequestPart("body") String body,
            @RequestPart(value = "recipientUserIds", required = false) String recipientUserIdsJson,
            @RequestPart(value = "tenantId", required = false) String tenantId,
            @RequestPart(value = "allSystem", required = false) String allSystemRaw,
            @RequestPart(value = "files", required = false) List<FilePart> files) {
        log.info("📥 POST /api/v1/notifications - title={}", title);
        boolean allSystem = "true".equalsIgnoreCase(allSystemRaw);
        List<String> recipientIds = parseRecipientIds(recipientUserIdsJson);
        List<FilePart> fileParts = files != null ? files : Collections.emptyList();

        return createNotificationUseCase
                .execute(title, body, recipientIds, blankToNull(tenantId), allSystem, fileParts)
                .map(result -> ResponseEntity.status(HttpStatus.CREATED)
                        .body((Object) CreateNotificationResponse.builder()
                                .recipientCount(result.recipientCount())
                                .attachmentCount(result.attachmentCount())
                                .message("Aviso enviado para " + result.recipientCount() + " destinatário(s)")
                                .build()))
                .onErrorResume(e -> errorResponse(e, HttpStatus.BAD_REQUEST));
    }

    @GetMapping
    public Mono<ResponseEntity<Object>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return listMyNotificationsUseCase.execute(unreadOnly, page, size)
                .map(result -> ResponseEntity.ok((Object) NotificationListResponse.builder()
                        .items(result.items().stream()
                                .map(notificationMapper::toResponse)
                                .collect(Collectors.toList()))
                        .total(result.total())
                        .unreadCount(result.unreadCount())
                        .page(result.page())
                        .size(result.size())
                        .build()))
                .onErrorResume(e -> errorResponse(e, HttpStatus.UNAUTHORIZED));
    }

    @GetMapping("/unread-count")
    public Mono<ResponseEntity<Object>> unreadCount() {
        return getUnreadCountUseCase.execute()
                .map(count -> ResponseEntity.ok((Object) UnreadCountResponse.builder()
                        .unreadCount(count)
                        .build()))
                .onErrorResume(e -> errorResponse(e, HttpStatus.UNAUTHORIZED));
    }

    @PatchMapping("/{id}/read")
    public Mono<ResponseEntity<Object>> markRead(@PathVariable String id) {
        return markNotificationReadUseCase.markOne(id)
                .map(n -> ResponseEntity.ok((Object) notificationMapper.toResponse(n)))
                .onErrorResume(e -> errorResponse(e, HttpStatus.BAD_REQUEST));
    }

    @PostMapping("/read-all")
    public Mono<ResponseEntity<Object>> markAllRead() {
        return markNotificationReadUseCase.markAll()
                .map(count -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("markedCount", count);
                    return ResponseEntity.ok((Object) body);
                })
                .onErrorResume(e -> errorResponse(e, HttpStatus.UNAUTHORIZED));
    }

    @GetMapping("/{id}/attachments/{fileId}")
    public Mono<ResponseEntity<Object>> download(
            @PathVariable String id,
            @PathVariable String fileId) {
        return downloadNotificationAttachmentUseCase.execute(id, fileId)
                .map(result -> {
                    String encoded = URLEncoder.encode(result.fileName(), StandardCharsets.UTF_8)
                            .replace("+", "%20");
                    InputStreamResource resource = new InputStreamResource(result.inputStream());
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename*=UTF-8''" + encoded)
                            .contentType(MediaType.parseMediaType(result.contentType()))
                            .body((Object) resource);
                })
                .onErrorResume(e -> errorResponse(e, HttpStatus.FORBIDDEN));
    }

    private List<String> parseRecipientIds(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("[")) {
                return objectMapper.readValue(trimmed, new TypeReference<>() {
                });
            }
            return List.of(trimmed.replace("\"", ""));
        } catch (Exception e) {
            log.warn("Falha ao parsear recipientUserIds: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Mono<ResponseEntity<Object>> errorResponse(Throwable e, HttpStatus status) {
        log.warn("Notificações: {}", e.getMessage());
        Map<String, String> body = new HashMap<>();
        body.put("message", e.getMessage() != null ? e.getMessage() : "Erro");
        body.put("error", e.getClass().getSimpleName());
        return Mono.just(ResponseEntity.status(status).body((Object) body));
    }
}
