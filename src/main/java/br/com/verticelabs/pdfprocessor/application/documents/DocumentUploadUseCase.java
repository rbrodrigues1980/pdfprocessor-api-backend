package br.com.verticelabs.pdfprocessor.application.documents;

import br.com.verticelabs.pdfprocessor.domain.exceptions.DocumentoDuplicadoException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.InvalidCpfException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.InvalidPdfException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.PersonNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentStatus;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollDocument;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.domain.model.DetectedPage;
import br.com.verticelabs.pdfprocessor.domain.service.CpfValidationService;
import br.com.verticelabs.pdfprocessor.domain.service.DocumentTypeDetectionService;
import br.com.verticelabs.pdfprocessor.domain.service.GridFsService;
import br.com.verticelabs.pdfprocessor.domain.service.MonthYearDetectionService;
import br.com.verticelabs.pdfprocessor.domain.service.PdfService;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import br.com.verticelabs.pdfprocessor.infrastructure.tenant.ReactiveTenantContext;
import br.com.verticelabs.pdfprocessor.interfaces.documents.dto.UploadDocumentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentUploadUseCase {

    private final GridFsService gridFsService;
    private final PdfService pdfService;
    private final DocumentTypeDetectionService typeDetectionService;
    private final MonthYearDetectionService monthYearDetectionService;
    private final CpfValidationService cpfValidationService;
    private final PersonRepository personRepository;
    private final PayrollDocumentRepository documentRepository;
    private final DocumentProcessUseCase documentProcessUseCase;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    public Mono<UploadDocumentResponse> upload(FilePart filePart, String cpf, String nome) {
        return upload(filePart, cpf, nome, null);
    }

    /**
     * Upload de documento por personId - busca automaticamente CPF, nome e matrícula da pessoa
     * Processa automaticamente o documento após o upload
     */
    public Mono<UploadDocumentResponse> uploadByPersonId(FilePart filePart, String personId) {
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
                    return processUpload(filePart, normalizedCpf, person.getNome(), person.getMatricula(), person.getTenantId())
                            .flatMap(uploadResponse -> {
                                log.info("✓ Upload concluído. DocumentId: {}, Status: {}", 
                                        uploadResponse.getDocumentId(), uploadResponse.getStatus());
                                
                                // Iniciar processamento automático do documento
                                log.info("Iniciando processamento automático do documento: {}", uploadResponse.getDocumentId());
                                return documentProcessUseCase.processDocument(uploadResponse.getDocumentId())
                                        .map(processResponse -> {
                                            log.info("✓ Processamento iniciado. DocumentId: {}, Status: {}", 
                                                    uploadResponse.getDocumentId(), processResponse.getStatus());
                                            
                                            // Retornar resposta com status PROCESSING (após iniciar processamento)
                                            return UploadDocumentResponse.builder()
                                                    .documentId(uploadResponse.getDocumentId())
                                                    .status(processResponse.getStatus()) // PROCESSING
                                                    .tipoDetectado(uploadResponse.getTipoDetectado())
                                                    .build();
                                        })
                                        .onErrorResume(processError -> {
                                            log.warn("⚠ Upload bem-sucedido, mas falha ao iniciar processamento: {}", 
                                                    processError.getMessage());
                                            // Upload foi bem-sucedido, mas processamento falhou
                                            // Retornar resposta com status PENDING
                                            return Mono.just(UploadDocumentResponse.builder()
                                                    .documentId(uploadResponse.getDocumentId())
                                                    .status(uploadResponse.getStatus()) // PENDING
                                                    .tipoDetectado(uploadResponse.getTipoDetectado())
                                                    .build());
                                        });
                            });
                });
    }

    public Mono<UploadDocumentResponse> upload(FilePart filePart, String cpf, String nome, String matricula) {
        log.info("=== INÍCIO DO UPLOAD ===");
        log.info("Arquivo: {}, CPF: {}, Nome: {}, Matrícula: {}", 
                filePart.filename(), cpf, nome != null ? nome : "não informado", 
                matricula != null ? matricula : "não informada");
        
        // Obter tenantId do contexto reativo
        return ReactiveTenantContext.getTenantId()
                .flatMap(tenantId -> {
                    log.info("🔐 Upload para tenant: {}", tenantId);
                    
                    // 1. Validar CPF
                    String normalizedCpf = cpfValidationService.normalize(cpf);
                    if (!cpfValidationService.isValid(normalizedCpf)) {
                        log.error("CPF inválido: {}", cpf);
                        return Mono.error(new InvalidCpfException("CPF inválido: " + cpf));
                    }
                    log.info("CPF validado: {}", normalizedCpf);
                    
                    return processUpload(filePart, normalizedCpf, nome, matricula, tenantId);
                });
    }
    
    private Mono<UploadDocumentResponse> processUpload(FilePart filePart, String cpf, String nome, String matricula, String tenantId) {

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
                    // Calcular hash dos bytes
                    log.info("Calculando hash SHA-256 do arquivo...");
                    return calculateFileHashFromBytes(fileBytes)
                            .flatMap(fileHash -> {
                                log.info("Hash calculado: {}", fileHash);
                                log.info("Verificando duplicidade para tenant: {}", tenantId);
                                return documentRepository.findByTenantIdAndFileHash(tenantId, fileHash)
                                    .flatMap(existingDoc -> {
                                        log.warn("Arquivo duplicado detectado! DocumentId existente: {}", existingDoc.getId());
                                        return Mono.<UploadDocumentResponse>error(new DocumentoDuplicadoException(
                                                "Este arquivo já foi enviado anteriormente. DocumentId: " + existingDoc.getId()));
                                    })
                                    .switchIfEmpty(
                                            // Se não existe duplicado, continuar processamento
                                            Mono.defer(() -> {
                                                log.info("Arquivo não é duplicado. Iniciando processamento...");
                                                log.info("Passando matrícula para processNewDocument: {}", matricula != null ? matricula : "null");
                                                return processNewDocument(fileBytes, cpf, nome, matricula, fileHash, filePart.filename(), tenantId);
                                            })
                                    );
                            });
                });
    }

    private Mono<UploadDocumentResponse> processNewDocument(byte[] fileBytes, String cpf, String nome, String matricula, String fileHash, String filename, String tenantId) {
        log.info("Processando novo documento para tenant: {}", tenantId);
        log.info("Arquivo em memória. Tamanho: {} bytes", fileBytes.length);
        
        // Extrair texto do PDF completo
        log.info("Extraindo texto do PDF...");
        return pdfService.extractText(new java.io.ByteArrayInputStream(fileBytes))
                            .flatMap(pdfText -> {
                                log.info("Texto extraído. Tamanho: {} caracteres", pdfText != null ? pdfText.length() : 0);
                                // Detectar tipo do documento
                                return typeDetectionService.detectType(pdfText);
                            })
                            .flatMap(documentType -> {
                                log.info("Tipo detectado: {}", documentType);
                                // Garantir que Person existe
                                return ensurePersonExists(cpf, nome, matricula, tenantId)
                                        .flatMap(person -> {
                                            log.info("Person encontrada/criada. CPF: {}", person.getCpf());
                                            // Salvar arquivo no GridFS com deduplicação por hash
                                            log.info("Salvando arquivo no GridFS com deduplicação (hash: {})...", 
                                                    fileHash.substring(0, 16) + "...");
                                            return gridFsService.storeFileWithHash(
                                                    new java.io.ByteArrayInputStream(fileBytes),
                                                    filename,
                                                    PDF_CONTENT_TYPE,
                                                    fileHash
                                            )
                                            .flatMap(fileId -> {
                                                log.info("Arquivo salvo no GridFS com ID: {}", fileId);
                                                // Processar páginas para detectar meses/anos
                                                log.info("Processando páginas do PDF para detectar meses/anos...");
                                                return processPages(fileBytes, documentType)
                                                    .flatMap(pageData -> {
                                                        List<String> mesesDetectados = pageData.mesesDetectados;
                                                        List<DetectedPage> detectedPages = pageData.detectedPages;
                                                        Integer anoDetectado = pageData.anoDetectado;
                                                        
                                                        log.info("Páginas processadas: {} páginas, {} meses detectados", 
                                                                detectedPages.size(), mesesDetectados.size());
                                                        log.info("Meses detectados: {}", mesesDetectados);
                                                        
                                                        // Criar PayrollDocument
                                                        log.info("Criando PayrollDocument para tenant: {}", tenantId);
                                                        PayrollDocument document = PayrollDocument.builder()
                                                                .tenantId(tenantId)
                                                                .cpf(cpf)
                                                                .tipo(documentType)
                                                                .status(DocumentStatus.PENDING)
                                                                .originalFileId(fileId)
                                                                .fileHash(fileHash)
                                                                .anoDetectado(anoDetectado)
                                                                .mesesDetectados(mesesDetectados)
                                                                .detectedPages(detectedPages)
                                                                .dataUpload(Instant.now())
                                                                .build();

                                                        return documentRepository.save(document)
                                                                .flatMap(savedDoc -> {
                                                                    log.info("PayrollDocument salvo. ID: {}", savedDoc.getId());
                                                                    // Apenas adicionar o documento à lista de documentos da Person
                                                                    // Nome, CPF e matrícula já foram salvos no ensurePersonExists()
                                                                    if (!person.getDocumentos().contains(savedDoc.getId())) {
                                                                        person.getDocumentos().add(savedDoc.getId());
                                                                        log.info("Adicionando documento {} à lista de documentos da Person (CPF: {})", 
                                                                                savedDoc.getId(), person.getCpf());
                                                                        log.info("Person atual - Nome: {}, Matrícula: {}", 
                                                                                person.getNome(), person.getMatricula());
                                                                        // Salvar apenas para atualizar a lista de documentos
                                                                        return personRepository.save(person)
                                                                                .doOnNext(savedPerson -> {
                                                                                    log.info("✅ Person atualizada com novo documento. ID: {}, Nome: {}, Matrícula: {}, Total de documentos: {}", 
                                                                                            savedPerson.getId(), savedPerson.getNome(), savedPerson.getMatricula(), 
                                                                                            savedPerson.getDocumentos().size());
                                                                                })
                                                                                .thenReturn(savedDoc);
                                                                    } else {
                                                                        log.debug("Documento {} já está na lista de documentos da Person", savedDoc.getId());
                                                                        return Mono.just(savedDoc);
                                                                    }
                                                                })
                                                                // Construir resposta
                                                                .map(savedDoc -> {
                                                                    log.info("=== UPLOAD CONCLUÍDO COM SUCESSO ===");
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
        return filePart.content()
                .collectList()
                .map(dataBuffers -> {
                    int totalSize = dataBuffers.stream()
                            .mapToInt(buffer -> buffer.readableByteCount())
                            .sum();
                    
                    byte[] bytes = new byte[totalSize];
                    int offset = 0;
                    for (org.springframework.core.io.buffer.DataBuffer buffer : dataBuffers) {
                        int readable = buffer.readableByteCount();
                        buffer.read(bytes, offset, readable);
                        offset += readable;
                    }
                    return new java.io.ByteArrayInputStream(bytes);
                });
    }

    private Mono<Person> ensurePersonExists(String cpf, String nome, String matricula, String tenantId) {
        log.info("=== ensurePersonExists() INICIADO ===");
        log.info("CPF: {}, Nome: {}, Matrícula: {}, TenantId: {}", cpf, nome, matricula, tenantId);
        
        // Normalizar dados
        String normalizedNome = nome != null && !nome.trim().isEmpty() ? nome.trim().toUpperCase() : null;
        String normalizedMatricula = matricula != null && !matricula.trim().isEmpty() 
                ? matricula.replaceAll("[^0-9]", "") : null; // Remove tudo que não é dígito
        
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
                    
                    // Atualizar matrícula (sempre que fornecida e válida)
                    if (normalizedMatricula != null && normalizedMatricula.length() == 7) {
                        // Matrícula válida (7 dígitos) - sempre atualizar se diferente ou null
                        if (existingPerson.getMatricula() == null || 
                            !normalizedMatricula.equals(existingPerson.getMatricula())) {
                            log.info("🔄 Atualizando matrícula: '{}' -> '{}'", 
                                    existingPerson.getMatricula() != null ? existingPerson.getMatricula() : "null", 
                                    normalizedMatricula);
                            existingPerson.setMatricula(normalizedMatricula);
                            needsUpdate = true;
                        } else {
                            log.debug("Matrícula já está atualizada: {}", normalizedMatricula);
                        }
                    } else if (normalizedMatricula != null && normalizedMatricula.length() != 7) {
                        log.warn("⚠️ Matrícula não tem 7 dígitos após normalização: '{}' (tamanho: {}). Será ignorada.", 
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
                                    .matricula(normalizedMatricula != null && normalizedMatricula.length() == 7 
                                            ? normalizedMatricula : null)
                                    .createdAt(Instant.now())
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

    private static class PageData {
        List<String> mesesDetectados;
        List<DetectedPage> detectedPages;
        Integer anoDetectado;
        
        PageData(List<String> mesesDetectados, List<DetectedPage> detectedPages, Integer anoDetectado) {
            this.mesesDetectados = mesesDetectados;
            this.detectedPages = detectedPages;
            this.anoDetectado = anoDetectado;
        }
    }

    private Mono<PageData> processPages(byte[] fileBytes, br.com.verticelabs.pdfprocessor.domain.model.DocumentType documentType) {
        return pdfService.getTotalPages(new java.io.ByteArrayInputStream(fileBytes))
                .flatMap(totalPages -> {
                    log.info("PDF possui {} páginas. Processando cada página...", totalPages);
                    
                    if (totalPages == 0) {
                        return Mono.just(new PageData(new ArrayList<>(), new ArrayList<>(), null));
                    }
                    
                    // Processar cada página
                    return Flux.range(1, totalPages)
                            .flatMap(pageNumber -> {
                                log.debug("Processando página {}/{}", pageNumber, totalPages);
                                return pdfService.extractTextFromPage(new java.io.ByteArrayInputStream(fileBytes), pageNumber)
                                                .flatMap(pageText -> {
                                                    // Detectar mês/ano
                                                    return monthYearDetectionService.detectMonthYear(pageText)
                                                            .flatMap(monthYearOpt -> {
                                                                // Detectar origem da página
                                                                return typeDetectionService.detectType(pageText)
                                                                        .map(pageType -> {
                                                                            DetectedPage detectedPage = DetectedPage.builder()
                                                                                    .page(pageNumber)
                                                                                    .origem(pageType.name())
                                                                                    .build();
                                                                            
                                                                            return new PageResult(pageNumber, monthYearOpt, detectedPage);
                                                                        });
                                                            });
                                                });
                                    })
                                    .collectList()
                                    .map(pageResults -> {
                                        Set<String> mesesSet = new HashSet<>();
                                        List<DetectedPage> detectedPages = new ArrayList<>();
                                        Integer anoDetectado = null;
                                        
                                        for (PageResult result : pageResults) {
                                            detectedPages.add(result.detectedPage);
                                            
                                            if (result.monthYear.isPresent()) {
                                                String monthYear = result.monthYear.get();
                                                mesesSet.add(monthYear);
                                                
                                                // Extrair ano (primeiros 4 caracteres)
                                                try {
                                                    int ano = Integer.parseInt(monthYear.substring(0, 4));
                                                    if (anoDetectado == null || ano > anoDetectado) {
                                                        anoDetectado = ano;
                                                    }
                                                } catch (NumberFormatException e) {
                                                    log.warn("Erro ao extrair ano de: {}", monthYear);
                                                }
                                            }
                                        }
                                        
                                        List<String> mesesDetectados = mesesSet.stream()
                                                .sorted()
                                                .collect(Collectors.toList());
                                        
                                        return new PageData(mesesDetectados, detectedPages, anoDetectado);
                                    });
                });
    }

    private static class PageResult {
        int pageNumber;
        java.util.Optional<String> monthYear;
        DetectedPage detectedPage;
        
        PageResult(int pageNumber, java.util.Optional<String> monthYear, DetectedPage detectedPage) {
            this.pageNumber = pageNumber;
            this.monthYear = monthYear;
            this.detectedPage = detectedPage;
        }
    }
}
