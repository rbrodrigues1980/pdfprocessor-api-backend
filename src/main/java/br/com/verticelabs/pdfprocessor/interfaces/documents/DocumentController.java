package br.com.verticelabs.pdfprocessor.interfaces.documents;

import br.com.verticelabs.pdfprocessor.application.documents.BulkDocumentUploadUseCase;
import br.com.verticelabs.pdfprocessor.application.documents.DocumentDeleteUseCase;
import br.com.verticelabs.pdfprocessor.application.documents.DocumentProcessUseCase;
import br.com.verticelabs.pdfprocessor.application.documents.DocumentQueryUseCase;
import br.com.verticelabs.pdfprocessor.application.documents.DocumentSummaryUseCase;
import br.com.verticelabs.pdfprocessor.application.documents.DocumentUploadUseCase;
import br.com.verticelabs.pdfprocessor.application.entries.EntryQueryUseCase;
import br.com.verticelabs.pdfprocessor.application.incometax.IncomeTaxUploadUseCase;
import br.com.verticelabs.pdfprocessor.domain.exceptions.InvalidStatusTransitionException;
import br.com.verticelabs.pdfprocessor.interfaces.documents.dto.ReprocessResponse;
import br.com.verticelabs.pdfprocessor.interfaces.entries.EntryMapper;
import br.com.verticelabs.pdfprocessor.interfaces.entries.dto.EntryResponse;
import br.com.verticelabs.pdfprocessor.interfaces.entries.dto.PagedEntriesResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

        private final DocumentUploadUseCase documentUploadUseCase;
        private final BulkDocumentUploadUseCase bulkDocumentUploadUseCase;
        private final DocumentProcessUseCase documentProcessUseCase;
        private final DocumentDeleteUseCase documentDeleteUseCase;
        private final DocumentQueryUseCase documentQueryUseCase;
        private final DocumentSummaryUseCase documentSummaryUseCase;
        private final EntryQueryUseCase entryQueryUseCase;
        private final EntryMapper entryMapper;
        private final IncomeTaxUploadUseCase incomeTaxUploadUseCase;

        @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public Mono<ResponseEntity<Object>> upload(
                        @RequestPart("file") FilePart file,
                        @RequestPart("cpf") String cpf,
                        @RequestPart(value = "nome", required = false) String nome) {
                return documentUploadUseCase.upload(file, cpf, nome)
                                .<ResponseEntity<Object>>map(response -> ResponseEntity.status(HttpStatus.CREATED)
                                                .body((Object) response));
        }

        /**
         * POST /api/v1/documents/bulk-upload
         * Upload múltiplo de arquivos para uma pessoa.
         * Requer: CPF, Nome, Matrícula (obrigatórios) e múltiplos arquivos PDF.
         */
        @PostMapping(value = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public Mono<ResponseEntity<Object>> bulkUpload(
                        @RequestPart("files") List<org.springframework.http.codec.multipart.FilePart> files,
                        @RequestPart("cpf") String cpf,
                        @RequestPart("nome") String nome,
                        @RequestPart("matricula") String matricula) {

                log.info("=== INÍCIO: POST /api/v1/documents/bulk-upload ===");
                log.info("Total de arquivos: {}, CPF: {}, Nome: {}, Matrícula: {}",
                                files != null ? files.size() : 0, cpf, nome, matricula);

                return bulkDocumentUploadUseCase.uploadBulk(files, cpf, nome, matricula)
                                .<ResponseEntity<Object>>map(response -> {
                                        log.info("=== SUCESSO: BulkUpload concluído ===");
                                        log.info("CPF: {}, Total: {}, Sucessos: {}, Falhas: {}",
                                                        response.getCpf(), response.getTotalArquivos(),
                                                        response.getSucessos(), response.getFalhas());
                                        return ResponseEntity.status(HttpStatus.CREATED).body((Object) response);
                                });
        }

        @PostMapping("/{id}/process")
        public Mono<ResponseEntity<Object>> processDocument(@PathVariable String id) {
                return documentProcessUseCase.processDocument(id)
                                .<ResponseEntity<Object>>map(response -> ResponseEntity.status(HttpStatus.ACCEPTED)
                                                .body((Object) response));
        }

        @DeleteMapping("/{id}")
        public Mono<ResponseEntity<Object>> deleteDocument(@PathVariable String id) {
                return documentDeleteUseCase.deleteDocument(id)
                                .then(Mono.just(ResponseEntity.noContent().<Object>build()));
        }

        /**
         * GET /api/v1/documents/{id}/entries
         * Retorna todas as entries de um documento.
         */
        @GetMapping("/{id}/entries")
        public Mono<ResponseEntity<List<EntryResponse>>> getEntriesByDocument(@PathVariable String id) {
                // Buscar entries e mapear com informações da rubrica quando disponível
                // Usar GLOBAL como tenantId padrão (rubricas são globais por padrão)
                return entryQueryUseCase.findByDocumentId(id)
                                .flatMap(entry -> entryMapper.toResponseWithRubrica(entry, "GLOBAL"))
                                .collectList()
                                .map(entries -> {
                                        if (entries.isEmpty()) {
                                                return ResponseEntity.status(HttpStatus.NO_CONTENT).body(entries);
                                        }
                                        return ResponseEntity.ok(entries);
                                });
        }

        /**
         * GET /api/v1/documents/{id}/entries/paged
         * Retorna entries paginadas de um documento.
         */
        @GetMapping("/{id}/entries/paged")
        public Mono<ResponseEntity<PagedEntriesResponse>> getEntriesByDocumentPaged(
                        @PathVariable String id,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        @RequestParam(defaultValue = "referencia") String sortBy,
                        @RequestParam(defaultValue = "asc") String sortDirection) {
                return entryQueryUseCase.findByDocumentIdPaged(id, page, size, sortBy, sortDirection)
                                .flatMap(pageResult -> {
                                        // Mapear entries com informações da rubrica
                                        return Flux.fromIterable(pageResult.getContent())
                                                        .flatMap(entry -> entryMapper.toResponseWithRubrica(entry,
                                                                        "GLOBAL"))
                                                        .collectList()
                                                        .map(content -> {
                                                                PagedEntriesResponse response = PagedEntriesResponse
                                                                                .builder()
                                                                                .content(content)
                                                                                .totalElements(pageResult
                                                                                                .getTotalElements())
                                                                                .totalPages(pageResult.getTotalPages())
                                                                                .currentPage(pageResult.getNumber())
                                                                                .pageSize(pageResult.getSize())
                                                                                .hasNext(pageResult.hasNext())
                                                                                .hasPrevious(pageResult.hasPrevious())
                                                                                .build();

                                                                return ResponseEntity.ok(response);
                                                        });
                                });
        }

        /**
         * GET /api/v1/documents/{id}
         * Retorna detalhes completos de um documento.
         */
        @GetMapping("/{id}")
        public Mono<ResponseEntity<Object>> getDocument(@PathVariable String id) {
                log.info("=== INÍCIO: GET /api/v1/documents/{} ===", id);

                return documentQueryUseCase.findById(id)
                                .<ResponseEntity<Object>>map(response -> {
                                        log.info("=== SUCESSO: Documento encontrado ===");
                                        log.info("ID: {}, Status: {}, CPF: {}", response.getId(), response.getStatus(),
                                                        response.getCpf());
                                        return ResponseEntity.ok((Object) response);
                                });
        }

        /**
         * GET /api/v1/documents
         * Consulta geral de documentos com filtros.
         */
        @GetMapping
        public Mono<ResponseEntity<Object>> getDocuments(
                        @RequestParam(required = false) String cpf,
                        @RequestParam(required = false) Integer ano,
                        @RequestParam(required = false) String status,
                        @RequestParam(required = false) String tipo,
                        @RequestParam(required = false) Long minEntries,
                        @RequestParam(required = false) Long maxEntries) {
                log.info("=== INÍCIO: GET /api/v1/documents ===");
                log.info("Filtros - CPF: {}, Ano: {}, Status: {}, Tipo: {}, MinEntries: {}, MaxEntries: {}",
                                cpf, ano, status, tipo, minEntries, maxEntries);

                return documentQueryUseCase.findByFilters(cpf, ano, status, tipo, minEntries, maxEntries)
                                .collectList()
                                .<ResponseEntity<Object>>map(documents -> {
                                        log.info("=== SUCESSO: {} documentos encontrados ===", documents.size());
                                        return ResponseEntity.ok((Object) documents);
                                });
        }

        /**
         * GET /api/v1/documents/{id}/pages
         * Retorna identificação das páginas do documento.
         */
        @GetMapping("/{id}/pages")
        public Mono<ResponseEntity<Object>> getDocumentPages(@PathVariable String id) {
                log.info("=== INÍCIO: GET /api/v1/documents/{}/pages ===", id);

                return documentQueryUseCase.findPagesById(id)
                                .<ResponseEntity<Object>>map(response -> {
                                        log.info("=== SUCESSO: Páginas encontradas ===");
                                        log.info("Documento ID: {}, Total de páginas: {}", response.getDocumentId(),
                                                        response.getPages().size());
                                        return ResponseEntity.ok((Object) response);
                                });
        }

        /**
         * GET /api/v1/documents/{id}/summary
         * Retorna resumo das rubricas e estatísticas do documento.
         */
        @GetMapping("/{id}/summary")
        public Mono<ResponseEntity<Object>> getDocumentSummary(@PathVariable String id) {
                log.info("=== INÍCIO: GET /api/v1/documents/{}/summary ===", id);

                return documentSummaryUseCase.getSummary(id)
                                .<ResponseEntity<Object>>map(response -> {
                                        log.info("=== SUCESSO: Resumo gerado ===");
                                        log.info("Documento ID: {}, Entries: {}, Rubricas: {}",
                                                        response.getDocumentId(), response.getEntriesCount(),
                                                        response.getRubricasResumo().size());
                                        return ResponseEntity.ok((Object) response);
                                });
        }

        /**
         * POST /api/v1/documents/upload-income-tax
         * Upload de declaração de imposto de renda.
         * Apenas faz upload e salva o documento, sem gerar Excel.
         * Similar ao upload de contracheques, apenas salva o documento associado ao
         * CPF.
         */
        @PostMapping(value = "/upload-income-tax", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public Mono<ResponseEntity<Object>> uploadIncomeTaxDeclaration(
                        @RequestPart("file") FilePart file,
                        @RequestPart("cpf") String cpf) {
                log.info("=== INÍCIO: POST /api/v1/documents/upload-income-tax ===");
                log.info("Arquivo: {}, CPF: {}", file.filename(), cpf);

                return incomeTaxUploadUseCase.uploadIncomeTaxDeclaration(file, cpf)
                                .<ResponseEntity<Object>>map(response -> {
                                        log.info("=== SUCESSO: Declaração de IR enviada ===");
                                        log.info("DocumentId: {}, Tipo: {}, Status: {}",
                                                        response.getDocumentId(), response.getTipoDetectado(),
                                                        response.getStatus());
                                        return ResponseEntity.status(HttpStatus.CREATED).body((Object) response);
                                });
        }

        /**
         * POST /api/v1/documents/{id}/reprocess
         * Reprocessa um documento já enviado.
         */
        @PostMapping("/{id}/reprocess")
        public Mono<ResponseEntity<Object>> reprocessDocument(@PathVariable String id) {
                log.info("=== INÍCIO: POST /api/v1/documents/{}/reprocess ===", id);

                return documentQueryUseCase.findById(id)
                                .flatMap(document -> {
                                        // Validar se pode ser reprocessado (ERROR ou PROCESSED)
                                        if (document.getStatus() == br.com.verticelabs.pdfprocessor.domain.model.DocumentStatus.PENDING) {
                                                log.warn("=== ERRO: Documento com status PENDING não pode ser reprocessado ===");
                                                log.warn("ID: {}, Status atual: {}", id, document.getStatus());
                                                return Mono.error(new InvalidStatusTransitionException(
                                                                "Documento com status PENDING não pode ser reprocessado. Status atual: "
                                                                                + document.getStatus()));
                                        }

                                        log.info("✓ Documento validado para reprocessamento: {} - Status atual: {}", id,
                                                        document.getStatus());

                                        return documentProcessUseCase.processDocument(id)
                                                        .map(processResponse -> {
                                                                ReprocessResponse response = ReprocessResponse.builder()
                                                                                .documentId(id)
                                                                                .status(processResponse.getStatus())
                                                                                .message("Reprocessamento iniciado")
                                                                                .build();

                                                                log.info("=== SUCESSO: Reprocessamento iniciado ===");
                                                                log.info("Documento ID: {}, Novo status: {}",
                                                                                response.getDocumentId(),
                                                                                response.getStatus());
                                                                return response;
                                                        });
                                })
                                .<ResponseEntity<Object>>map(response -> ResponseEntity.status(HttpStatus.ACCEPTED)
                                                .body((Object) response));
        }

        // Classe interna para respostas de erro

}
