package br.com.verticelabs.pdfprocessor.interfaces.persons;

import br.com.verticelabs.pdfprocessor.application.documents.BulkDocumentUploadUseCase;
import br.com.verticelabs.pdfprocessor.application.documents.DeleteDocumentUseCase;
import br.com.verticelabs.pdfprocessor.application.documents.DocumentQueryUseCase;
import br.com.verticelabs.pdfprocessor.application.documents.DocumentUploadUseCase;
import br.com.verticelabs.pdfprocessor.application.incometax.IncomeTaxUploadUseCase;
import br.com.verticelabs.pdfprocessor.application.entries.EntryQueryUseCase;
import br.com.verticelabs.pdfprocessor.application.persons.*;
import br.com.verticelabs.pdfprocessor.domain.exceptions.InvalidCpfException;
import br.com.verticelabs.pdfprocessor.domain.service.CpfValidationService;
import br.com.verticelabs.pdfprocessor.infrastructure.tenant.ReactiveTenantContext;
import br.com.verticelabs.pdfprocessor.interfaces.documents.dto.BulkUploadItemResponse;
import br.com.verticelabs.pdfprocessor.interfaces.documents.dto.BulkUploadResponse;
import br.com.verticelabs.pdfprocessor.interfaces.entries.EntryMapper;
import br.com.verticelabs.pdfprocessor.interfaces.entries.dto.PersonEntriesResponse;
import br.com.verticelabs.pdfprocessor.interfaces.persons.dto.CreatePersonRequest;
import br.com.verticelabs.pdfprocessor.interfaces.persons.dto.PersonListResponse;
import br.com.verticelabs.pdfprocessor.interfaces.persons.dto.PersonResponse;
import br.com.verticelabs.pdfprocessor.interfaces.persons.dto.PersonRubricasMatrixResponse;
import br.com.verticelabs.pdfprocessor.interfaces.persons.dto.UpdatePersonRequest;
import jakarta.validation.Valid;
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
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/persons")
@RequiredArgsConstructor
public class PersonController {

        private final DocumentQueryUseCase documentQueryUseCase;
        private final EntryQueryUseCase entryQueryUseCase;
        private final EntryMapper entryMapper;
        private final ListPersonsUseCase listPersonsUseCase;
        private final PersonRubricasMatrixUseCase personRubricasMatrixUseCase;
        private final PersonMapper personMapper;
        private final CreatePersonUseCase createPersonUseCase;
        private final UpdatePersonUseCase updatePersonUseCase;
        private final DeletePersonUseCase deletePersonUseCase;
        private final ActivatePersonUseCase activatePersonUseCase;
        private final DeactivatePersonUseCase deactivatePersonUseCase;
        private final GetPersonByIdUseCase getPersonByIdUseCase;
        private final DocumentUploadUseCase documentUploadUseCase;
        private final BulkDocumentUploadUseCase bulkDocumentUploadUseCase;
        private final DeleteDocumentUseCase deleteDocumentUseCase;
        private final IncomeTaxUploadUseCase incomeTaxUploadUseCase;
        private final CpfValidationService cpfValidationService;

        /**
         * POST /api/v1/persons
         * Cria uma nova pessoa.
         */
        @PostMapping
        public Mono<ResponseEntity<Object>> createPerson(@Valid @RequestBody CreatePersonRequest request) {
                log.info("📥 POST /api/v1/persons - Criar pessoa: CPF={}, Nome={}", request.getCpf(),
                                request.getNome());

                return createPersonUseCase.execute(request)
                                .map(person -> {
                                        PersonResponse response = personMapper.toResponse(person);
                                        return ResponseEntity.status(HttpStatus.CREATED).body((Object) response);
                                });
        }

        /**
         * GET /api/v1/persons/{id}
         * Busca uma pessoa por ID.
         */
        @GetMapping("/{id}")
        public Mono<ResponseEntity<Object>> getPersonById(@PathVariable String id) {
                log.info("📥 GET /api/v1/persons/{} - Buscar pessoa por ID", id);

                return getPersonByIdUseCase.execute(id)
                                .map(person -> {
                                        PersonResponse response = personMapper.toResponse(person);
                                        return ResponseEntity.ok((Object) response);
                                });
        }

        /**
         * PUT /api/v1/persons/{id}
         * Atualiza uma pessoa existente.
         */
        @PutMapping("/{id}")
        public Mono<ResponseEntity<Object>> updatePerson(
                        @PathVariable String id,
                        @Valid @RequestBody UpdatePersonRequest request) {
                log.info("📥 PUT /api/v1/persons/{} - Atualizar pessoa: Nome={}, Matrícula={}",
                                id, request.getNome(), request.getMatricula());

                return updatePersonUseCase.execute(id, request)
                                .map(person -> {
                                        PersonResponse response = personMapper.toResponse(person);
                                        return ResponseEntity.ok((Object) response);
                                });
        }

        /**
         * DELETE /api/v1/persons/{id}
         * Exclui definitivamente uma pessoa.
         */
        @DeleteMapping("/{id}")
        public Mono<ResponseEntity<Object>> deletePerson(@PathVariable String id) {
                log.info("📥 DELETE /api/v1/persons/{} - Excluir definitivamente pessoa", id);

                return deletePersonUseCase.execute(id)
                                .then(Mono.just(ResponseEntity.noContent().<Object>build()));
        }

        /**
         * PATCH /api/v1/persons/{id}/activate
         * Ativa uma pessoa.
         */
        @PatchMapping("/{id}/activate")
        public Mono<ResponseEntity<Object>> activatePerson(@PathVariable String id) {
                log.info("📥 PATCH /api/v1/persons/{}/activate - Ativar pessoa", id);

                return activatePersonUseCase.execute(id)
                                .map(person -> {
                                        PersonResponse response = personMapper.toResponse(person);
                                        return ResponseEntity.ok((Object) response);
                                });
        }

        /**
         * PATCH /api/v1/persons/{id}/deactivate
         * Desativa uma pessoa.
         */
        @PatchMapping("/{id}/deactivate")
        public Mono<ResponseEntity<Object>> deactivatePerson(@PathVariable String id) {
                log.info("📥 PATCH /api/v1/persons/{}/deactivate - Desativar pessoa", id);

                return deactivatePersonUseCase.execute(id)
                                .map(person -> {
                                        PersonResponse response = personMapper.toResponse(person);
                                        return ResponseEntity.ok((Object) response);
                                });
        }

        /**
         * GET /api/v1/persons
         * Lista todas as pessoas com paginação e filtros.
         */
        @GetMapping
        public Mono<ResponseEntity<PersonListResponse>> listPersons(
                        @RequestParam(required = false) String nome,
                        @RequestParam(required = false) String cpf,
                        @RequestParam(required = false) String matricula,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "100") int size) {
                log.info("📥 GET /api/v1/persons - Listar pessoas (page={}, size={})", page, size);

                return listPersonsUseCase.execute(nome, cpf, matricula, page, size)
                                .flatMap(result -> {
                                        List<PersonResponse> personResponses = result.persons().stream()
                                                        .map(personMapper::toResponse)
                                                        .collect(Collectors.toList());

                                        PersonListResponse response = PersonListResponse.builder()
                                                        .content(personResponses)
                                                        .totalElements(result.total())
                                                        .totalPages(result.totalPages())
                                                        .currentPage(result.page())
                                                        .pageSize(result.size())
                                                        .hasNext(result.page() < result.totalPages() - 1)
                                                        .hasPrevious(result.page() > 0)
                                                        .build();

                                        return Mono.just(ResponseEntity.ok(response));
                                });
        }

        /**
         * GET /api/v1/persons/{cpf}/entries
         * Retorna todas as entries de todos os documentos de uma pessoa.
         */
        @GetMapping("/{cpf}/entries")
        public Mono<ResponseEntity<PersonEntriesResponse>> getEntriesByPerson(
                        @PathVariable String cpf) {
                log.debug("GET /persons/{}/entries", cpf);

                return entryQueryUseCase.findByCpf(cpf)
                                .map(entryMapper::toResponse)
                                .collectList()
                                .flatMap(entries -> {
                                        PersonEntriesResponse response = PersonEntriesResponse.builder()
                                                        .cpf(cpf)
                                                        .totalEntries((long) entries.size())
                                                        .entries(entries)
                                                        .build();

                                        if (entries.isEmpty()) {
                                                return Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT)
                                                                .body(response));
                                        }
                                        return Mono.just(ResponseEntity.ok(response));
                                });
        }

        /**
         * GET /api/v1/persons/{personId}/documents
         * Lista todos os documentos de uma pessoa específica (por personId).
         * Este endpoint é preferível quando se tem o personId, pois garante que apenas
         * os documentos
         * da pessoa específica sejam retornados, mesmo quando há múltiplas pessoas com
         * o mesmo CPF.
         */
        @GetMapping("/{personId}/documents-by-id")
        public Mono<ResponseEntity<Object>> getDocumentsByPersonId(@PathVariable String personId) {
                log.info("=== INÍCIO: GET /api/v1/persons/{}/documents-by-id ===", personId);

                return documentQueryUseCase.findByPersonId(personId)
                                .<ResponseEntity<Object>>map(response -> {
                                        log.info("=== SUCESSO: Documentos encontrados ===");
                                        log.info("PersonId: {}, Total de documentos: {}", personId,
                                                        response.getDocumentos().size());
                                        return ResponseEntity.ok((Object) response);
                                });
        }

        /**
         * GET /api/v1/persons/{cpf}/documents
         * Lista todos os documentos de um CPF.
         * NOTA: Se houver múltiplas pessoas com o mesmo CPF em diferentes tenants,
         * este endpoint retornará documentos de todas elas (para SUPER_ADMIN) ou apenas
         * do tenant do usuário.
         * Para garantir documentos de uma pessoa específica, use
         * /{personId}/documents-by-id
         */
        @GetMapping("/{cpf}/documents")
        public Mono<ResponseEntity<Object>> getDocumentsByCpf(@PathVariable String cpf) {
                log.info("=== INÍCIO: GET /api/v1/persons/{}/documents ===", cpf);

                return documentQueryUseCase.findByCpf(cpf)
                                .<ResponseEntity<Object>>map(response -> {
                                        log.info("=== SUCESSO: Documentos encontrados ===");
                                        log.info("CPF: {}, Total de documentos: {}", response.getCpf(),
                                                        response.getDocumentos().size());
                                        return ResponseEntity.ok((Object) response);
                                });
        }

        /**
         * POST /api/v1/persons/{personId}/documents/upload
         * Upload de um único documento para uma pessoa específica.
         * Busca automaticamente CPF, nome e matrícula da pessoa.
         */
        @PostMapping(value = "/{personId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public Mono<ResponseEntity<Object>> uploadDocumentByPersonId(
                        @PathVariable String personId,
                        @RequestPart("file") FilePart file) {
                log.info("📥 POST /api/v1/persons/{}/documents/upload - Upload de documento", personId);

                return documentUploadUseCase.uploadByPersonId(file, personId)
                                .<ResponseEntity<Object>>map(response -> {
                                        log.info("✓ Documento enviado com sucesso: DocumentId={}, Status={}",
                                                        response.getDocumentId(), response.getStatus());
                                        return ResponseEntity.status(HttpStatus.CREATED).body((Object) response);
                                });
        }

        /**
         * POST /api/v1/persons/{personId}/documents/bulk-upload
         * Upload múltiplo de documentos para uma pessoa específica.
         * Busca automaticamente CPF, nome e matrícula da pessoa.
         */
        @PostMapping(value = "/{personId}/documents/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public Mono<ResponseEntity<Object>> bulkUploadDocumentsByPersonId(
                        @PathVariable String personId,
                        @RequestPart("files") List<FilePart> files) {
                log.info("📥 POST /api/v1/persons/{}/documents/bulk-upload - Upload múltiplo de documentos", personId);
                log.info("Total de arquivos: {}", files != null ? files.size() : 0);

                return bulkDocumentUploadUseCase.uploadBulkByPersonId(files, personId)
                                .<ResponseEntity<Object>>map(response -> {
                                        log.info("✓ BulkUpload concluído: Total={}, Sucessos={}, Falhas={}",
                                                        response.getTotalArquivos(), response.getSucessos(),
                                                        response.getFalhas());
                                        return ResponseEntity.status(HttpStatus.CREATED).body((Object) response);
                                });
        }

        /**
         * POST /api/v1/persons/{personId}/income-tax/upload
         * Upload de uma declaração de imposto de renda para uma pessoa específica.
         * Busca automaticamente CPF da pessoa.
         */
        @PostMapping(value = "/{personId}/income-tax/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public Mono<ResponseEntity<Object>> uploadIncomeTaxByPersonId(
                        @PathVariable String personId,
                        @RequestPart("file") FilePart file) {
                log.info("📥 POST /api/v1/persons/{}/income-tax/upload - Upload de declaração de IR", personId);

                return incomeTaxUploadUseCase.uploadIncomeTaxByPersonId(file, personId)
                                .<ResponseEntity<Object>>map(response -> {
                                        log.info("✓ Declaração de IR enviada com sucesso: DocumentId={}, Status={}",
                                                        response.getDocumentId(), response.getStatus());
                                        return ResponseEntity.status(HttpStatus.CREATED).body((Object) response);
                                });
        }

        /**
         * POST /api/v1/persons/{personId}/income-tax/bulk-upload
         * Upload múltiplo de declarações de imposto de renda para uma pessoa
         * específica.
         * Busca automaticamente CPF da pessoa.
         */
        @PostMapping(value = "/{personId}/income-tax/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public Mono<ResponseEntity<Object>> bulkUploadIncomeTaxByPersonId(
                        @PathVariable String personId,
                        @RequestPart("files") List<FilePart> files) {
                log.info("📥 POST /api/v1/persons/{}/income-tax/bulk-upload - Upload múltiplo de declarações de IR",
                                personId);
                log.info("Total de arquivos: {}", files != null ? files.size() : 0);

                if (files == null || files.isEmpty()) {
                        log.error("Nenhum arquivo enviado para bulk upload de declarações de IR");
                        return Mono.error(new IllegalArgumentException("Pelo menos um arquivo deve ser enviado"));
                }

                // Processar cada arquivo sequencialmente
                return getPersonByIdUseCase.execute(personId)
                                .flatMap(person -> {
                                        log.info("✓ Pessoa encontrada: CPF={}, Nome={}, TenantId={}",
                                                        person.getCpf(), person.getNome(), person.getTenantId());

                                        // Validar CPF
                                        String normalizedCpf = cpfValidationService.normalize(person.getCpf());
                                        if (!cpfValidationService.isValid(normalizedCpf)) {
                                                log.error("CPF inválido: {}", person.getCpf());
                                                return Mono.error(new InvalidCpfException(
                                                                "CPF inválido: " + person.getCpf()));
                                        }

                                        // Processar cada arquivo
                                        return Flux.fromIterable(files)
                                                        .index()
                                                        .concatMap(tuple -> {
                                                                long index = tuple.getT1();
                                                                FilePart file = tuple.getT2();
                                                                String filename = file.filename();

                                                                log.info("Processando declaração de IR {}/{}: {}",
                                                                                index + 1,
                                                                                files.size(), filename);

                                                                return ReactiveTenantContext.withTenant(
                                                                                incomeTaxUploadUseCase
                                                                                                .uploadIncomeTaxDeclaration(
                                                                                                                file,
                                                                                                                normalizedCpf),
                                                                                person.getTenantId())
                                                                                .map(response -> {
                                                                                        log.info("✓ Declaração de IR {} enviada: DocumentId={}, Status={}",
                                                                                                        filename,
                                                                                                        response.getDocumentId(),
                                                                                                        response.getStatus());
                                                                                        return BulkUploadItemResponse
                                                                                                        .builder()
                                                                                                        .filename(filename)
                                                                                                        .documentId(response
                                                                                                                        .getDocumentId())
                                                                                                        .status(response.getStatus())
                                                                                                        .tipoDetectado(response
                                                                                                                        .getTipoDetectado())
                                                                                                        .sucesso(true)
                                                                                                        .build();
                                                                                })
                                                                                .onErrorResume(error -> {
                                                                                        log.error("✗ Erro ao fazer upload da declaração de IR {}: {}",
                                                                                                        filename,
                                                                                                        error.getMessage());
                                                                                        return Mono.just(
                                                                                                        BulkUploadItemResponse
                                                                                                                        .builder()
                                                                                                                        .filename(filename)
                                                                                                                        .sucesso(false)
                                                                                                                        .erro(error.getMessage())
                                                                                                                        .build());
                                                                                });
                                                        })
                                                        .collectList()
                                                        .map(resultados -> {
                                                                int totalArquivos = resultados.size();
                                                                long sucessos = resultados.stream()
                                                                                .filter(BulkUploadItemResponse::getSucesso)
                                                                                .count();
                                                                long falhas = totalArquivos - sucessos;

                                                                log.info("=== BulkUpload de Declarações de IR CONCLUÍDO ===");
                                                                log.info("Total: {}, Sucessos: {}, Falhas: {}",
                                                                                totalArquivos,
                                                                                sucessos, falhas);

                                                                return BulkUploadResponse.builder()
                                                                                .cpf(normalizedCpf)
                                                                                .totalArquivos(totalArquivos)
                                                                                .sucessos((int) sucessos)
                                                                                .falhas((int) falhas)
                                                                                .resultados(resultados)
                                                                                .build();
                                                        });
                                })
                                .<ResponseEntity<Object>>map(response -> {
                                        log.info("✓ BulkUpload de declarações de IR concluído: Total={}, Sucessos={}, Falhas={}",
                                                        response.getTotalArquivos(), response.getSucessos(),
                                                        response.getFalhas());
                                        return ResponseEntity.status(HttpStatus.CREATED).body((Object) response);
                                });
        }

        /**
         * DELETE /api/v1/persons/{personId}/documents/{documentId}
         * Exclui um documento e todas as suas referências:
         * - PayrollDocument
         * - PayrollEntry relacionadas
         * - Arquivo no GridFS (fs.files e fs.chunks)
         * - Referência do documento na lista de documentos da Person
         */
        @DeleteMapping("/{personId}/documents/{documentId}")
        public Mono<ResponseEntity<Object>> deleteDocument(
                        @PathVariable String personId,
                        @PathVariable String documentId) {
                log.info("📥 DELETE /api/v1/persons/{}/documents/{} - Excluir documento", personId, documentId);

                return deleteDocumentUseCase.execute(documentId)
                                .then(Mono.defer(() -> {
                                        log.info("✓ Documento excluído com sucesso: {}", documentId);
                                        return Mono.<ResponseEntity<Object>>just(ResponseEntity.noContent().build());
                                }))
                                .defaultIfEmpty(ResponseEntity.noContent().<Object>build());
        }

        /**
         * GET /api/v1/persons/{cpf}/rubricas
         * Retorna as rubricas de uma pessoa em formato de matriz, com totais.
         * Matriz: rubricaCodigo -> referencia (mês/ano) -> valor e quantidade
         */
        @GetMapping("/{cpf}/rubricas")
        public Mono<ResponseEntity<PersonRubricasMatrixResponse>> getPersonRubricasMatrix(@PathVariable String cpf) {
                log.info("📥 GET /api/v1/persons/{}/rubricas - Matriz de rubricas", cpf);

                return personRubricasMatrixUseCase.execute(cpf)
                                .flatMap(useCaseResponse -> {
                                        PersonRubricasMatrixResponse response = personMapper
                                                        .toMatrixResponse(useCaseResponse);

                                        // Se a matriz está vazia, retornar 200 OK com dados vazios (não é erro)
                                        if (response.getMatrix() == null || response.getMatrix().isEmpty()) {
                                                log.info("✓ Matriz de rubricas vazia para pessoa: {} (sem dados processados)",
                                                                cpf);
                                        } else {
                                                log.info("✓ Matriz de rubricas retornada: {} rubricas, total geral: R$ {}",
                                                                response.getMatrix().size(), response.getTotalGeral());
                                        }

                                        return Mono.just(ResponseEntity.ok(response));
                                });
        }

}
