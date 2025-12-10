package br.com.verticelabs.pdfprocessor.application.documents;

import br.com.verticelabs.pdfprocessor.domain.exceptions.InvalidCpfException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.PersonNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.domain.service.CpfValidationService;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import br.com.verticelabs.pdfprocessor.infrastructure.tenant.ReactiveTenantContext;
import br.com.verticelabs.pdfprocessor.interfaces.documents.dto.BulkUploadItemResponse;
import br.com.verticelabs.pdfprocessor.interfaces.documents.dto.BulkUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkDocumentUploadUseCase {

    private final DocumentUploadUseCase documentUploadUseCase;
    private final DocumentProcessUseCase documentProcessUseCase;
    private final CpfValidationService cpfValidationService;
    private final PersonRepository personRepository;

    /**
     * Realiza upload de múltiplos arquivos para uma pessoa
     * 
     * @param files Lista de arquivos PDF
     * @param cpf CPF da pessoa (obrigatório)
     * @param nome Nome da pessoa (obrigatório)
     * @param matricula Matrícula da pessoa (obrigatório)
     * @return Resposta com resultado de cada upload
     */
    public Mono<BulkUploadResponse> uploadBulk(
            List<FilePart> files,
            String cpf,
            String nome,
            String matricula) {
        
        log.info("=== INÍCIO: BulkDocumentUploadUseCase.uploadBulk() ===");
        log.info("Total de arquivos: {}, CPF: {}, Nome: {}, Matrícula: {}", 
                files != null ? files.size() : 0, cpf, nome, matricula);

        // Validar parâmetros obrigatórios
        if (cpf == null || cpf.trim().isEmpty()) {
            log.error("CPF não informado");
            return Mono.error(new IllegalArgumentException("CPF é obrigatório"));
        }

        if (nome == null || nome.trim().isEmpty()) {
            log.error("Nome não informado");
            return Mono.error(new IllegalArgumentException("Nome é obrigatório"));
        }

        if (matricula == null || matricula.trim().isEmpty()) {
            log.error("Matrícula não informada");
            return Mono.error(new IllegalArgumentException("Matrícula é obrigatória"));
        }

        if (files == null || files.isEmpty()) {
            log.error("Nenhum arquivo enviado");
            return Mono.error(new IllegalArgumentException("Pelo menos um arquivo deve ser enviado"));
        }

        // Validar CPF
        String normalizedCpf = cpfValidationService.normalize(cpf);
        if (!cpfValidationService.isValid(normalizedCpf)) {
            log.error("CPF inválido: {}", cpf);
            return Mono.error(new InvalidCpfException("CPF inválido: " + cpf));
        }

        log.info("✓ Parâmetros validados. CPF normalizado: {}", normalizedCpf);

        // Processar cada arquivo em paralelo (ou sequencialmente para evitar sobrecarga)
        // Usando concatMap para processar sequencialmente e evitar sobrecarga do sistema
        return Flux.fromIterable(files)
                .index() // Adiciona índice para rastreamento
                .concatMap(tuple -> {
                    long index = tuple.getT1();
                    FilePart file = tuple.getT2();
                    String filename = file.filename();
                    
                    log.info("Processando arquivo {}/{}: {}", index + 1, files.size(), filename);
                    
                    // 1. Fazer upload do arquivo
                    return documentUploadUseCase.upload(file, normalizedCpf, nome, matricula)
                            .flatMap(uploadResponse -> {
                                log.info("✓ Upload concluído para arquivo {}. DocumentId: {}, Status: {}", 
                                        filename, uploadResponse.getDocumentId(), uploadResponse.getStatus());
                                
                                // 2. Iniciar processamento automático do documento
                                log.info("Iniciando processamento automático do documento: {}", uploadResponse.getDocumentId());
                                return documentProcessUseCase.processDocument(uploadResponse.getDocumentId())
                                        .map(processResponse -> {
                                            log.info("✓ Processamento iniciado para arquivo {}. DocumentId: {}, Status: {}", 
                                                    filename, uploadResponse.getDocumentId(), processResponse.getStatus());
                                            
                                            return BulkUploadItemResponse.builder()
                                                    .filename(filename)
                                                    .documentId(uploadResponse.getDocumentId())
                                                    .status(processResponse.getStatus()) // Status após iniciar processamento (PROCESSING)
                                                    .tipoDetectado(uploadResponse.getTipoDetectado())
                                                    .sucesso(true)
                                                    .build();
                                        })
                                        .onErrorResume(processError -> {
                                            log.warn("⚠ Upload bem-sucedido, mas falha ao iniciar processamento para arquivo {}: {}", 
                                                    filename, processError.getMessage());
                                            // Upload foi bem-sucedido, mas processamento falhou
                                            return Mono.just(BulkUploadItemResponse.builder()
                                                    .filename(filename)
                                                    .documentId(uploadResponse.getDocumentId())
                                                    .status(uploadResponse.getStatus()) // Status do upload (PENDING)
                                                    .tipoDetectado(uploadResponse.getTipoDetectado())
                                                    .sucesso(true) // Upload foi bem-sucedido
                                                    .erro("Upload concluído, mas processamento não pôde ser iniciado: " + processError.getMessage())
                                                    .build());
                                        });
                            })
                            .onErrorResume(error -> {
                                log.error("✗ Erro ao fazer upload do arquivo {}: {}", filename, error.getMessage());
                                return Mono.just(BulkUploadItemResponse.builder()
                                        .filename(filename)
                                        .sucesso(false)
                                        .erro(error.getMessage())
                                        .build());
                            });
                })
                .collectList()
                .map(resultados -> {
                    int totalArquivos = resultados.size();
                    long sucessos = resultados.stream().filter(BulkUploadItemResponse::getSucesso).count();
                    long falhas = totalArquivos - sucessos;

                    log.info("=== BulkUpload CONCLUÍDO ===");
                    log.info("Total: {}, Sucessos: {}, Falhas: {}", totalArquivos, sucessos, falhas);

                    return BulkUploadResponse.builder()
                            .cpf(normalizedCpf)
                            .totalArquivos(totalArquivos)
                            .sucessos((int) sucessos)
                            .falhas((int) falhas)
                            .resultados(resultados)
                            .build();
                });
    }

    /**
     * Realiza upload de múltiplos arquivos para uma pessoa específica (por personId)
     * Busca automaticamente CPF, nome e matrícula da pessoa
     * 
     * @param files Lista de arquivos PDF
     * @param personId ID da pessoa
     * @return Resposta com resultado de cada upload
     */
    public Mono<BulkUploadResponse> uploadBulkByPersonId(
            List<FilePart> files,
            String personId) {
        
        log.info("=== INÍCIO: BulkDocumentUploadUseCase.uploadBulkByPersonId() ===");
        log.info("Total de arquivos: {}, PersonId: {}", 
                files != null ? files.size() : 0, personId);

        if (personId == null || personId.trim().isEmpty()) {
            log.error("PersonId não informado");
            return Mono.error(new IllegalArgumentException("PersonId é obrigatório"));
        }

        if (files == null || files.isEmpty()) {
            log.error("Nenhum arquivo enviado");
            return Mono.error(new IllegalArgumentException("Pelo menos um arquivo deve ser enviado"));
        }

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
                    
                    // Usar o tenantId da pessoa e configurá-lo no contexto reativo antes de fazer upload
                    return ReactiveTenantContext.withTenant(
                            uploadBulk(files, normalizedCpf, person.getNome(), person.getMatricula()),
                            person.getTenantId()
                    );
                });
    }
}

