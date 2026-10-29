package br.com.verticelabs.pdfprocessor.application.conversations;

import br.com.verticelabs.pdfprocessor.domain.model.ConversationMessage.Attachment;
import br.com.verticelabs.pdfprocessor.domain.service.GridFsService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ConversationAttachmentHelper {

    public static final int MAX_ATTACHMENTS = 10;
    public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

    private final GridFsService gridFsService;

    public Mono<List<Attachment>> storeAttachments(List<FilePart> files) {
        List<FilePart> fileParts = files != null ? files : List.of();
        if (fileParts.size() > MAX_ATTACHMENTS) {
            return Mono.error(new IllegalArgumentException(
                    "Máximo de " + MAX_ATTACHMENTS + " anexos por mensagem"));
        }
        if (fileParts.isEmpty()) {
            return Mono.just(List.of());
        }
        return Flux.fromIterable(fileParts)
                .concatMap(this::storeOne)
                .collectList();
    }

    public static List<Attachment> copyAttachments(List<Attachment> source) {
        List<Attachment> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }
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
}
