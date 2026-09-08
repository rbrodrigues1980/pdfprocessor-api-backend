package br.com.verticelabs.pdfprocessor.application.documents;

import br.com.verticelabs.pdfprocessor.domain.exceptions.DocumentoDuplicadoException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.InvalidCpfException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.InvalidPdfException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.PersonNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentStatus;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollDocument;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.domain.service.CpfValidationService;
import br.com.verticelabs.pdfprocessor.domain.service.GridFsService;
import br.com.verticelabs.pdfprocessor.domain.service.MatriculaNormalizer;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import br.com.verticelabs.pdfprocessor.infrastructure.tenant.ReactiveTenantContext;
import br.com.verticelabs.pdfprocessor.interfaces.documents.dto.UploadDocumentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentUploadUseCase {

    private final GridFsService gridFsService;
    private final CpfValidationService cpfValidationService;
    private final PersonRepository personRepository;
    private final PayrollDocumentRepository documentRepository;
    private final DocumentProcessUseCase documentProcessUseCase;
    private final DeleteDocumentUseCase deleteDocumentUseCase;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    public Mono<UploadDocumentResponse> upload(FilePart filePart, String cpf, String nome) {
        return upload(filePart, cpf, nome, null, false);
    }

    public Mono<UploadDocumentResponse> upload(FilePart filePart, String cpf, String nome, boolean replaceIfDuplicate) {
        return upload(filePart, cpf, nome, null, replaceIfDuplicate);
    }

    /**
     * Upload de documento por personId - busca automaticamente CPF, nome e matrícula da pessoa
     * Processa automaticamente o documento após o upload
     */
    public Mono<UploadDocumentResponse> uploadByPersonId(FilePart filePart, String personId) {
        return uploadByPersonId(filePart, personId, false);
    }

    public Mono<UploadDocumentResponse> uploadByPersonId(FilePart filePart, String personId, boolean replaceIfDuplicate) {
        log.info("=== INÍCIO DO UPLOAD POR PERSONID ===");
        log.info("Arquivo: {}, PersonId: {}", filePart.filename(), personId);
        
        // Buscar pessoa por ID com validação de acesso
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    if (Boolean.TRUE.equals(isSuperAdmin)) {
                        // SUPER_ADMIN pode buscar qualquer pessoa
                        return personRepository.findById(personId)
                                .switchIfEmpty(Mono.error(new PersonNotFoundException("ID: " + personId)));
                    } else {
                        // Outros usuários só podem buscar pessoas do seu tenant
                        return ReactiveSecurityContextHelper.getTenantId()
                                .flatMap(tenantId -> personRepository.findByTenantIdAndId(tenantId, personId)
                                        .switchIfEmpty(Mono.error(new PersonNotFoundException("ID: " + personId))));
                    }
                })
                .flatMap(person -> {
                    log.info("✓ Pessoa encontrada: CPF={}, Nome={}, Matrícula={}, TenantId={}", 
                            person.getCpf(), person.getNome(), person.getMatricula(), person.getTenantId());
                    
                    // Validar CPF
                    String normalizedCpf = cpfValidationService.normalize(person.getCpf());
                    if (!cpfValidationService.isValid(normalizedCpf)) {
                        log.error("CPF inválido: {}", person.getCpf());
                        return Mono.error(new InvalidCpfException("CPF inválido: " + person.getCpf()));
                    }
                    log.info("CPF validado: {}", normalizedCpf);
                    
                    // Usar o tenantId da pessoa diretamente e fazer upload
                    return processUpload(filePart, normalizedCpf, person.getNome(), person.getMatricula(), person.getTenantId(), replaceIfDuplicate)
                            .flatMap(uploadResponse -> {
                                log.info("✓ Upload concluído. DocumentId: {}, Status: {}", 
                                        uploadResponse.getDocumentId(), uploadResponse.getStatus());
                                
                                // Disparar processamento sem bloquear a resposta HTTP
                                triggerProcessingAsync(uploadResponse.getDocumentId(), person.getTenantId());

                                return Mono.just(UploadDocumentResponse.builder()
                                        .documentId(uploadResponse.getDocumentId())
                                        .status(DocumentStatus.PROCESSING)
                                        .tipoDetectado(uploadResponse.getTipoDetectado())
                                        .build());
                            });
                });
    }

    public Mono<UploadDocumentResponse> upload(FilePart filePart, String cpf, String nome, String matricula) {
        return upload(filePart, cpf, nome, matricula, false);
    }

    public Mono<UploadDocumentResponse> upload(FilePart filePart, String cpf, String nome, String matricula,
            boolean replaceIfDuplicate) {
        log.info("=== INÍCIO DO UPLOAD ===");
        log.info("Arquivo: {}, CPF: {}, Nome: {}, Matrícula: {}, replaceIfDuplicate: {}",
                filePart.filename(), cpf, nome != null ? nome : "não informado",
                matricula != null ? matricula : "não informada", replaceIfDuplicate);

        return ReactiveTenantContext.getTenantId()
                .flatMap(tenantId -> {
                    log.info("🔐 Upload para tenant: {}", tenantId);

                    String normalizedCpf = cpfValidationService.normalize(cpf);
                    if (!cpfValidationService.isValid(normalizedCpf)) {
                        log.error("CPF inválido: {}", cpf);
                        return Mono.error(new InvalidCpfException("CPF inválido: " + cpf));
                    }
                    log.info("CPF validado: {}", normalizedCpf);

                    return processUpload(filePart, normalizedCpf, nome, matricula, tenantId, replaceIfDuplicate)
                            .map(uploadResponse -> {
                                triggerProcessingAsync(uploadResponse.getDocumentId(), tenantId);
                                return UploadDocumentResponse.builder()
                                        .documentId(uploadResponse.getDocumentId())
                                        .status(DocumentStatus.PROCESSING)
                                        .tipoDetectado(uploadResponse.getTipoDetectado())
                                        .build();
                            });
                });
    }

    private void triggerProcessingAsync(String documentId, String tenantId) {
        log.info("Iniciando processamento automático do documento: {}", documentId);
        ReactiveTenantContext.withTenant(
                documentProcessUseCase.processDocument(documentId),
                tenantId
        ).subscribe(
                processResponse -> log.info(
                        "✓ Processamento iniciado. DocumentId: {}, Status: {}",
                        documentId, processResponse.getStatus()),
                processError -> log.warn(
                        "⚠ Upload bem-sucedido, mas falha ao iniciar processamento: {}",
                        processError.getMessage()));
    }

    private Mono<UploadDocumentResponse> processUpload(FilePart filePart, String cpf, String nome, String matricula,
            String tenantId, boolean replaceIfDuplicate) {

        // 2. Validar arquivo
        return validateFile(filePart)
                .flatMap(valid -> {
                    if (!valid) {
                        return Mono.error(new InvalidPdfException("Arquivo inválido. Deve ser um PDF válido."));
                    }
                    return Mono.just(valid);
                })
                // 3. Ler conteúdo do arquivo UMA VEZ e armazenar em memória
                .flatMap(v -> {
                    log.info("Lendo conteúdo do arquivo para calcular hash e processar...");
                    return readFileContent(filePart)
                            .flatMap(inputStream -> {
                                // Ler todos os bytes e calcular hash ao mesmo tempo
                                return Mono.fromCallable(() -> {
                                    byte[] fileBytes = inputStream.readAllBytes();
                                    inputStream.close();
                                    return fileBytes;
                                }).subscribeOn(Schedulers.boundedElastic());
                            });
                })
                .flatMap(fileBytes -> {
                    log.info("Arquivo lido em memória. Tamanho: {} bytes", fileBytes.length);
                    if (fileBytes.length > MAX_FILE_SIZE) {
                        return Mono.error(new InvalidPdfException(
                                "Arquivo excede o limite de 10 MB."));
                    }
                    // Calcular hash dos bytes
                    log.info("Calculando hash SHA-256 do arquivo...");
                    return calculateFileHashFromBytes(fileBytes)
                            .flatMap(fileHash -> {
                                log.info("Hash calculado: {}", fileHash);
                                log.info("Verificando duplicidade para tenant: {}", tenantId);
                                return documentRepository.findByTenantIdAndFileHash(tenantId, fileHash)
                                    .flatMap(existingDoc -> handleDuplicateDocument(
                                            existingDoc.getId(), replaceIfDuplicate,
                                            () -> processNewDocument(fileBytes, cpf, nome, matricula, fileHash, filePart.filename(), tenantId)))
                                    .switchIfEmpty(
                                            Mono.defer(() -> {
                                                log.info("Arquivo não é duplicado. Iniciando processamento...");
                                                return processNewDocument(fileBytes, cpf, nome, matricula, fileHash, filePart.filename(), tenantId);
                                            })
                                    );
                            });
                });
    }

    private Mono<UploadDocumentResponse> processNewDocument(byte[] fileBytes, String cpf, String nome, String matricula, String fileHash, String filename, String tenantId) {
        log.info("Upload rápido: salvando documento sem extração de texto (tenant: {})", tenantId);
        log.info("Arquivo em memória. Tamanho: {} bytes", fileBytes.length);

        return ensurePersonExists(cpf, nome, matricula, tenantId)
                .flatMap(person -> {
                    log.info("Person encontrada/criada. CPF: {}", person.getCpf());
                    log.info("Salvando arquivo no GridFS (hash: {})...", fileHash.substring(0, 16) + "...");
                    return gridFsService.storeFileWithHash(
                            new java.io.ByteArrayInputStream(fileBytes),
                            filename,
                            PDF_CONTENT_TYPE,
                            fileHash
                    ).flatMap(fileId -> {
                        log.info("Arquivo salvo no GridFS com ID: {}", fileId);
                        PayrollDocument document = PayrollDocument.builder()
                                .tenantId(tenantId)
                                .cpf(cpf)
                                .tipo(DocumentType.UNKNOWN)
                                .status(DocumentStatus.PENDING)
                                .originalFileId(fileId)
                                .fileHash(fileHash)
                                .mesesDetectados(new ArrayList<>())
                                .detectedPages(new ArrayList<>())
                                .dataUpload(Instant.now())
                                .build();

                        return documentRepository.save(document)
                                .flatMap(savedDoc -> {
                                    if (!person.getDocumentos().contains(savedDoc.getId())) {
                                        person.getDocumentos().add(savedDoc.getId());
                                        person.setUpdatedAt(Instant.now());
                                        return personRepository.save(person).thenReturn(savedDoc);
                                    }
                                    return Mono.just(savedDoc);
                                })
                                .map(savedDoc -> {
                                    log.info("=== UPLOAD RÁPIDO CONCLUÍDO ===");
                                    log.info("DocumentId: {}, Tipo: {}, Status: {}",
                                            savedDoc.getId(), savedDoc.getTipo(), savedDoc.getStatus());
                                    return UploadDocumentResponse.builder()
                                            .documentId(savedDoc.getId())
                                            .status(savedDoc.getStatus())
                                            .tipoDetectado(savedDoc.getTipo())
                                            .build();
                                });
                    });
                });
    }

    private Mono<String> calculateFileHashFromBytes(byte[] fileBytes) {
        return Mono.fromCallable(() -> {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(fileBytes);
                byte[] hashBytes = digest.digest();
                
                // Converter para hexadecimal
                StringBuilder sb = new StringBuilder();
                for (byte b : hashBytes) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (Exception e) {
                throw new RuntimeException("Erro ao calcular hash do arquivo", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Boolean> validateFile(FilePart filePart) {
        // Validar extensão
        String filename = filePart.filename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            return Mono.just(false);
        }

        // Validar tamanho (será validado durante o upload)
        return Mono.just(true);
    }

    private Mono<java.io.InputStream> readFileContent(FilePart filePart) {
        // Escreve o conteúdo em arquivo temporário liberando cada DataBuffer conforme consumido,
        // evitando manter todos os buffers diretos (NIO/Netty) em memória ao mesmo tempo
        // (causa de OutOfMemoryError de direct buffer no upload múltiplo).
        return Mono.fromCallable(() -> java.nio.file.Files.createTempFile("pdf-upload-", ".tmp"))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(tempPath -> org.springframework.core.io.buffer.DataBufferUtils
                        .write(filePart.content(), tempPath)
                        .then(Mono.fromCallable(() -> {
                            try {
                                byte[] bytes = java.nio.file.Files.readAllBytes(tempPath);
                                return (java.io.InputStream) new java.io.ByteArrayInputStream(bytes);
                            } finally {
                                java.nio.file.Files.deleteIfExists(tempPath);
                            }
                        }).subscribeOn(Schedulers.boundedElastic())));
    }

    private Mono<Person> ensurePersonExists(String cpf, String nome, String matricula, String tenantId) {
        log.info("=== ensurePersonExists() INICIADO ===");
        log.info("CPF: {}, Nome: {}, Matrícula: {}, TenantId: {}", cpf, nome, matricula, tenantId);
        
        // Normalizar dados
        String normalizedNome = nome != null && !nome.trim().isEmpty() ? nome.trim().toUpperCase() : null;
        String normalizedMatricula = MatriculaNormalizer.toDigitsOrNull(matricula);
        
        log.info("Dados normalizados - Nome: {}, Matrícula: {}", normalizedNome, normalizedMatricula);
        
        return personRepository.findByTenantIdAndCpf(tenantId, cpf)
                .flatMap(existingPerson -> {
                    log.info("Person já existe. Nome atual: {}, Matrícula atual: {}", 
                            existingPerson.getNome(), existingPerson.getMatricula());
                    
                    boolean needsUpdate = false;
                    
                    // Atualizar nome (sempre em MAIÚSCULAS)
                    if (normalizedNome != null) {
                        if (existingPerson.getNome() == null || 
                            !existingPerson.getNome().trim().equalsIgnoreCase(normalizedNome)) {
                            log.info("🔄 Atualizando nome: '{}' -> '{}'", existingPerson.getNome(), normalizedNome);
                            existingPerson.setNome(normalizedNome);
                            needsUpdate = true;
                        } else {
                            log.debug("Nome já está atualizado: {}", normalizedNome);
                        }
                    }
                    
                    // Matrícula: preencher se cadastro vazio; se diverge, só warn (CPF do cadastro é a chave)
                    if (MatriculaNormalizer.isValid(normalizedMatricula)) {
                        String matCadastro = MatriculaNormalizer.toDigitsOrNull(existingPerson.getMatricula());
                        if (matCadastro == null) {
                            log.info("🔄 Preenchendo matrícula do cadastro: '{}'", normalizedMatricula);
                            existingPerson.setMatricula(normalizedMatricula);
                            needsUpdate = true;
                        } else if (!normalizedMatricula.equals(matCadastro)) {
                            log.warn(
                                    "⚠️ Matrícula do PDF/upload ({}) diverge da matrícula do cadastro ({}). Mantendo cadastro.",
                                    normalizedMatricula, matCadastro);
                        } else {
                            log.debug("Matrícula já está atualizada: {}", normalizedMatricula);
                        }
                    } else if (normalizedMatricula != null) {
                        log.warn("⚠️ Matrícula inválida após normalização: '{}' (tamanho: {}). Aceito 7–8 dígitos. Será ignorada.", 
                                normalizedMatricula, normalizedMatricula.length());
                    }
                    
                    // SEMPRE salvar se houver alguma atualização (nome ou matrícula)
                    if (needsUpdate) {
                        existingPerson.setUpdatedAt(Instant.now());
                        log.info("Salvando Person atualizada...");
                        return personRepository.save(existingPerson)
                                .doOnNext(saved -> {
                                    log.info("✅ Person atualizada com sucesso! CPF: {}, Nome: {}, Matrícula: {}", 
                                            saved.getCpf(), saved.getNome(), saved.getMatricula());
                                });
                    } else {
                        log.info("Person já está atualizada (nome e matrícula corretos), não precisa salvar");
                        log.info("Person atual - CPF: {}, Nome: {}, Matrícula: {}", 
                                existingPerson.getCpf(), existingPerson.getNome(), existingPerson.getMatricula());
                        return Mono.just(existingPerson);
                    }
                })
                .switchIfEmpty(
                        // Criar nova pessoa
                        Mono.defer(() -> {
                            log.info("Person não existe. Criando nova Person para tenant: {}", tenantId);
                            log.info("CPF: {}, Nome: {}, Matrícula: {}", cpf, normalizedNome, normalizedMatricula);
                            
                            Person newPerson = Person.builder()
                                    .tenantId(tenantId)
                                    .cpf(cpf)
                                    .nome(normalizedNome)
                                    .matricula(MatriculaNormalizer.isValid(normalizedMatricula) ? normalizedMatricula : null)
                                    .createdAt(Instant.now())
                                    .updatedAt(Instant.now())
                                    .build();
                            
                            log.info("Person criada (antes de salvar): Nome: {}, Matrícula: {}", 
                                    newPerson.getNome(), newPerson.getMatricula());
                            
                            return personRepository.save(newPerson)
                                    .doOnNext(saved -> {
                                        log.info("✅ Person criada com sucesso! Nome: {}, Matrícula: {}", 
                                                saved.getNome(), saved.getMatricula());
                                    });
                        })
                );
    }

    /**
     * Trata arquivo duplicado: substitui (exclui + novo upload) ou retorna 409 com ID do documento existente.
     */
    private Mono<UploadDocumentResponse> handleDuplicateDocument(
            String existingDocumentId,
            boolean replaceIfDuplicate,
            Supplier<Mono<UploadDocumentResponse>> createNewDocument) {
        log.warn("Arquivo duplicado detectado! DocumentId existente: {}", existingDocumentId);
        if (replaceIfDuplicate) {
            log.info("replaceIfDuplicate=true — excluindo documento {} (entries, GridFS, Person) e reenviando...",
                    existingDocumentId);
            return deleteDocumentUseCase.execute(existingDocumentId)
                    .then(Mono.defer(createNewDocument));
        }
        return Mono.error(new DocumentoDuplicadoException(existingDocumentId));
    }
}
