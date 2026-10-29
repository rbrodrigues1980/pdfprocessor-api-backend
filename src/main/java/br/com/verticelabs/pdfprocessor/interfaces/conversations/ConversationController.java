package br.com.verticelabs.pdfprocessor.interfaces.conversations;

import br.com.verticelabs.pdfprocessor.application.conversations.CreateConversationUseCase;
import br.com.verticelabs.pdfprocessor.application.conversations.DownloadConversationAttachmentUseCase;
import br.com.verticelabs.pdfprocessor.application.conversations.GetConversationUnreadCountUseCase;
import br.com.verticelabs.pdfprocessor.application.conversations.ListConversationMessagesUseCase;
import br.com.verticelabs.pdfprocessor.application.conversations.ListConversationsUseCase;
import br.com.verticelabs.pdfprocessor.application.conversations.MarkConversationReadUseCase;
import br.com.verticelabs.pdfprocessor.application.conversations.ReplyConversationUseCase;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import br.com.verticelabs.pdfprocessor.interfaces.conversations.dto.ConversationListResponse;
import br.com.verticelabs.pdfprocessor.interfaces.conversations.dto.ConversationMessagesResponse;
import br.com.verticelabs.pdfprocessor.interfaces.conversations.dto.CreateConversationResponse;
import br.com.verticelabs.pdfprocessor.interfaces.conversations.dto.UnreadConversationsResponse;
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
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final CreateConversationUseCase createConversationUseCase;
    private final ListConversationsUseCase listConversationsUseCase;
    private final GetConversationUnreadCountUseCase getConversationUnreadCountUseCase;
    private final ListConversationMessagesUseCase listConversationMessagesUseCase;
    private final ReplyConversationUseCase replyConversationUseCase;
    private final MarkConversationReadUseCase markConversationReadUseCase;
    private final DownloadConversationAttachmentUseCase downloadConversationAttachmentUseCase;
    private final ConversationMapper conversationMapper;
    private final ObjectMapper objectMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Object>> create(
            @RequestPart("title") String title,
            @RequestPart("body") String body,
            @RequestPart(value = "recipientUserIds", required = false) String recipientUserIdsJson,
            @RequestPart(value = "tenantId", required = false) String tenantId,
            @RequestPart(value = "allSystem", required = false) String allSystemRaw,
            @RequestPart(value = "files", required = false) List<FilePart> files) {
        log.info("📥 POST /api/v1/conversations - title={}", title);
        boolean allSystem = "true".equalsIgnoreCase(allSystemRaw);
        List<String> recipientIds = parseRecipientIds(recipientUserIdsJson);
        List<FilePart> fileParts = files != null ? files : Collections.emptyList();

        return createConversationUseCase
                .execute(title, body, recipientIds, blankToNull(tenantId), allSystem, fileParts)
                .map(result -> ResponseEntity.status(HttpStatus.CREATED)
                        .body((Object) CreateConversationResponse.builder()
                                .conversationId(result.conversationId())
                                .participantCount(result.participantCount())
                                .attachmentCount(result.attachmentCount())
                                .message("Conversa criada com " + result.participantCount() + " participante(s)")
                                .build()))
                .onErrorResume(e -> errorResponse(e, statusFor(e)));
    }

    @GetMapping
    public Mono<ResponseEntity<Object>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return listConversationsUseCase.execute(unreadOnly, page, size)
                .map(result -> ResponseEntity.ok((Object) ConversationListResponse.builder()
                        .items(result.items().stream()
                                .map(c -> conversationMapper.toResponse(c, result.currentUserId()))
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
        return getConversationUnreadCountUseCase.execute()
                .map(count -> ResponseEntity.ok((Object) UnreadConversationsResponse.builder()
                        .unreadCount(count)
                        .build()))
                .onErrorResume(e -> errorResponse(e, HttpStatus.UNAUTHORIZED));
    }

    @GetMapping("/{id}/messages")
    public Mono<ResponseEntity<Object>> messages(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(userId -> listConversationMessagesUseCase.execute(id, page, size)
                        .map(result -> ResponseEntity.ok((Object) ConversationMessagesResponse.builder()
                                .conversationId(result.conversationId())
                                .title(result.title())
                                .items(result.items().stream()
                                        .map(m -> conversationMapper.toMessageResponse(m, userId))
                                        .collect(Collectors.toList()))
                                .total(result.total())
                                .page(result.page())
                                .size(result.size())
                                .build())))
                .onErrorResume(e -> errorResponse(e, statusFor(e)));
    }

    @PostMapping(value = "/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Object>> reply(
            @PathVariable String id,
            @RequestPart("body") String body,
            @RequestPart(value = "files", required = false) List<FilePart> files) {
        List<FilePart> fileParts = files != null ? files : Collections.emptyList();
        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(userId -> replyConversationUseCase.execute(id, body, fileParts)
                        .map(msg -> ResponseEntity.status(HttpStatus.CREATED)
                                .body((Object) conversationMapper.toMessageResponse(msg, userId))))
                .onErrorResume(e -> errorResponse(e, statusFor(e)));
    }

    @PostMapping("/{id}/read")
    public Mono<ResponseEntity<Object>> markRead(@PathVariable String id) {
        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(userId -> markConversationReadUseCase.execute(id)
                        .map(c -> ResponseEntity.ok((Object) conversationMapper.toResponse(c, userId))))
                .onErrorResume(e -> errorResponse(e, statusFor(e)));
    }

    @GetMapping("/{id}/messages/{messageId}/attachments/{fileId}")
    public Mono<ResponseEntity<Object>> download(
            @PathVariable String id,
            @PathVariable String messageId,
            @PathVariable String fileId) {
        return downloadConversationAttachmentUseCase.execute(id, messageId, fileId)
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
                .onErrorResume(e -> errorResponse(e, statusFor(e)));
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

    private static HttpStatus statusFor(Throwable e) {
        if (e instanceof SecurityException) {
            return HttpStatus.FORBIDDEN;
        }
        if (e instanceof IllegalArgumentException) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.BAD_REQUEST;
    }

    private Mono<ResponseEntity<Object>> errorResponse(Throwable e, HttpStatus status) {
        log.warn("Conversas: {}", e.getMessage());
        Map<String, String> body = new HashMap<>();
        body.put("message", e.getMessage() != null ? e.getMessage() : "Erro");
        body.put("error", e.getClass().getSimpleName());
        return Mono.just(ResponseEntity.status(status).body((Object) body));
    }
}
