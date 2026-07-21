package br.com.verticelabs.pdfprocessor.application.incometax;

import br.com.verticelabs.pdfprocessor.application.documents.DocumentProcessUseCase;
import br.com.verticelabs.pdfprocessor.application.persons.GetPersonByIdUseCase;
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
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService;
import br.com.verticelabs.pdfprocessor.infrastructure.tenant.ReactiveTenantContext;
import br.com.verticelabs.pdfprocessor.interfaces.documents.dto.UploadDocumentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncomeTaxUploadUseCase {

        private final IncomeTaxDeclarationService incomeTaxDeclarationService;
        private final GridFsService gridFsService;
        private final PersonRepository personRepository;
        private final PayrollDocumentRepository documentRepository;
        private final CpfValidationService cpfValidationService;
        private final DocumentProcessUseCase documentProcessUseCase;
        private final GetPersonByIdUseCase getPersonByIdUseCase;

        private static final String PDF_CONTENT_TYPE = "application/pdf";

        /**
         * Processa upload de declaração de imposto de renda.
         * Apenas faz upload e salva o documento, sem gerar Excel.
         * 
         * @param filePart Arquivo PDF da declaração
         * @param cpf      CPF da pessoa
         * @return Resposta com ID do documento criado
         */
        public Mono<UploadDocumentResponse> uploadIncomeTaxDeclaration(FilePart filePart, String cpf) {
                log.info("=== INÍCIO: Upload de declaração de IR ===");
                log.info("Arquivo: {}, CPF: {}", filePart.filename(), cpf);

                // 1. Validar CPF primeiro
                String normalizedCpf = cpfValidationService.normalize(cpf);
                if (!cpfValidationService.isValid(normalizedCpf)) {
                        log.error("CPF inválido: {}", cpf);
                        return Mono.error(new InvalidCpfException("CPF inválido: " + cpf));
                }
                log.info("CPF validado: {}", normalizedCpf);

                return ReactiveTenantContext.getTenantId()
                                .flatMap(tenantId -> {
                                        log.info("🔐 Upload para tenant: {}", tenantId);

                                        // 2. Verificar se pessoa existe
                                        return personRepository.findByTenantIdAndCpf(tenantId, normalizedCpf)
                                                        .switchIfEmpty(Mono.error(
                                                                        new PersonNotFoundException(
                                                                                        "Pessoa não encontrada para CPF: "
                                                                                                        + normalizedCpf)))
                                                        .flatMap(person -> {
                                                                log.info("Pessoa encontrada: {} ({})", person.getNome(),
                                                                                person.getCpf());

                                                                // 3. Ler arquivo e calcular hash
                                                                return readFileContent(filePart)
                                                                                .flatMap(inputStream -> {
                                                                                        return Mono.fromCallable(() -> {
                                                                                                byte[] fileBytes = inputStream
                                                                                                                .readAllBytes();
                                                                                                inputStream.close();
                                                                                                return fileBytes;
                                                                                        }).subscribeOn(Schedulers
                                                                                                        .boundedElastic());
                                                                                })
                                                                                .flatMap(fileBytes -> {
                                                                                        log.info("Arquivo lido em memória. Tamanho: {} bytes",
                                                                                                        fileBytes.length);

                                                                                        // Validar tamanho do arquivo
                                                                                        // (deve ser maior que 0)
                                                                                        if (fileBytes.length == 0) {
                                                                                                log.error("Arquivo vazio detectado. Tamanho: 0 bytes");
                                                                                                return Mono.error(
                                                                                                                new InvalidPdfException(
                                                                                                                                "Arquivo vazio ou corrompido. O arquivo deve ter pelo menos 1 byte."));
                                                                                        }

                                                                                        // Validar tamanho mínimo
                                                                                        // razoável para um PDF (pelo
                                                                                        // menos 100 bytes)
                                                                                        if (fileBytes.length < 100) {
                                                                                                log.warn("Arquivo muito pequeno. Tamanho: {} bytes. Pode estar corrompido.",
                                                                                                                fileBytes.length);
                                                                                        }

                                                                                        // 4. Calcular hash do arquivo
                                                                                        return calculateFileHash(
                                                                                                        fileBytes)
                                                                                                        .flatMap(fileHash -> {
                                                                                                                log.info("Hash calculado: {}",
                                                                                                                                fileHash);

                                                                                                                // 5.
                                                                                                                // Verificar
                                                                                                                // duplicidade
                                                                                                                return documentRepository
                                                                                                                                .findByTenantIdAndFileHash(
                                                                                                                                                tenantId,
                                                                                                                                                fileHash)
                                                                                                                                .flatMap(existingDoc -> {
                                                                                                                                        log.warn(
                                                                                                                                                        "Arquivo duplicado detectado! DocumentId existente: {}",
                                                                                                                                                        existingDoc.getId());
                                                                                                                                        return Mono.<UploadDocumentResponse>error(
                                                                                                                                                        new DocumentoDuplicadoException(
                                                                                                                                                                        existingDoc.getId()));
                                                                                                                                })
                                                                                                                                .switchIfEmpty(
                                                                                                                                                // 6.
                                                                                                                                                // Salvar
                                                                                                                                                // arquivo
                                                                                                                                                // no
                                                                                                                                                // GridFS
                                                                                                                                                gridFsService.storeFileWithHash(
                                                                                                                                                                new java.io.ByteArrayInputStream(
                                                                                                                                                                                fileBytes),
                                                                                                                                                                filePart.filename(),
                                                                                                                                                                PDF_CONTENT_TYPE,
                                                                                                                                                                fileHash)
                                                                                                                                                                .flatMap(fileId -> {
                                                                                                                                                                        log.info(
                                                                                                                                                                                        "Arquivo salvo no GridFS com ID: {}",
                                                                                                                                                                                        fileId);

                                                                                                                                                                        // 7.
                                                                                                                                                                        // Extrair
                                                                                                                                                                        // informações
                                                                                                                                                                        // da
                                                                                                                                                                        // declaração
                                                                                                                                                                        // (opcional,
                                                                                                                                                                        // para
                                                                                                                                                                        // metadata)
                                                                                                                                                                        return extractIncomeTaxMetadata(
                                                                                                                                                                                        new java.io.ByteArrayInputStream(
                                                                                                                                                                                                        fileBytes))
                                                                                                                                                                                        .flatMap(metadata -> {
                                                                                                                                                                                                // 8.
                                                                                                                                                                                                // Criar
                                                                                                                                                                                                // PayrollDocument
                                                                                                                                                                                                // com
                                                                                                                                                                                                // metadata
                                                                                                                                                                                                PayrollDocument document = PayrollDocument
                                                                                                                                                                                                                .builder()
                                                                                                                                                                                                                .tenantId(
                                                                                                                                                                                                                                tenantId)
                                                                                                                                                                                                                .cpf(normalizedCpf)
                                                                                                                                                                                                                .tipo(DocumentType.INCOME_TAX)
                                                                                                                                                                                                                .status(DocumentStatus.PENDING)
                                                                                                                                                                                                                .originalFileId(
                                                                                                                                                                                                                                fileId)
                                                                                                                                                                                                                .fileHash(
                                                                                                                                                                                                                                fileHash)
                                                                                                                                                                                                                .fileHash(
                                                                                                                                                                                                                                fileHash)
                                                                                                                                                                                                                .anoDetectado(
                                                                                                                                                                                                                                parseAnoCalendario(
                                                                                                                                                                                                                                                metadata.anoCalendario))
                                                                                                                                                                                                                .dataUpload(
                                                                                                                                                                                                                                Instant.now())
                                                                                                                                                                                                                .build();

                                                                                                                                                                                                return saveDocumentAndUpdatePerson(
                                                                                                                                                                                                                document,
                                                                                                                                                                                                                person);
                                                                                                                                                                                        })
                                                                                                                                                                                        .switchIfEmpty(
                                                                                                                                                                                                        // Se
                                                                                                                                                                                                        // não
                                                                                                                                                                                                        // conseguir
                                                                                                                                                                                                        // extrair
                                                                                                                                                                                                        // metadata,
                                                                                                                                                                                                        // salvar
                                                                                                                                                                                                        // documento
                                                                                                                                                                                                        // mesmo
                                                                                                                                                                                                        // assim
                                                                                                                                                                                                        Mono.defer(() -> {
                                                                                                                                                                                                                log.debug(
                                                                                                                                                                                                                                "Metadata não extraída, salvando documento sem ano detectado");
                                                                                                                                                                                                                PayrollDocument document = PayrollDocument
                                                                                                                                                                                                                                .builder()
                                                                                                                                                                                                                                .tenantId(
                                                                                                                                                                                                                                                tenantId)
                                                                                                                                                                                                                                .cpf(normalizedCpf)
                                                                                                                                                                                                                                .tipo(DocumentType.INCOME_TAX)
                                                                                                                                                                                                                                .status(DocumentStatus.PENDING)
                                                                                                                                                                                                                                .originalFileId(
                                                                                                                                                                                                                                                fileId)
                                                                                                                                                                                                                                .fileHash(
                                                                                                                                                                                                                                                fileHash)
                                                                                                                                                                                                                                .dataUpload(
                                                                                                                                                                                                                                                Instant.now())
                                                                                                                                                                                                                                .build();

                                                                                                                                                                                                                return saveDocumentAndUpdatePerson(
                                                                                                                                                                                                                                document,
                                                                                                                                                                                                                                person);
                                                                                                                                                                                                        }));
                                                                                                                                                                }));
                                                                                                        });
                                                                                });
                                                        });
                                });
        }

        /**
         * Salva o documento e atualiza a lista de documentos da Person.
         * Após salvar, inicia o processamento automático do documento.
         */
        private Mono<UploadDocumentResponse> saveDocumentAndUpdatePerson(PayrollDocument document, Person person) {
                return documentRepository.save(document)
                                .flatMap(savedDoc -> {
                                        log.info("PayrollDocument salvo. ID: {}", savedDoc.getId());

                                        // Adicionar documento à lista da Person
                                        if (!person.getDocumentos().contains(savedDoc.getId())) {
                                                person.getDocumentos().add(savedDoc.getId());
                                                return personRepository.save(person)
                                                                .thenReturn(savedDoc);
                                        }
                                        return Mono.just(savedDoc);
                                })
                                .flatMap(savedDoc -> {
                                        log.info("=== UPLOAD CONCLUÍDO COM SUCESSO ===");
                                        log.info("DocumentId: {}, Tipo: {}, Status: {}",
                                                        savedDoc.getId(), savedDoc.getTipo(), savedDoc.getStatus());

                                        // Iniciar processamento automático do documento
                                        // IMPORTANTE: Propagar o contexto do tenant para o processamento
                                        log.info("Iniciando processamento automático do documento de IR: {}",
                                                        savedDoc.getId());
                                        String docTenantId = savedDoc.getTenantId();

                                        return ReactiveTenantContext.withTenant(
                                                        documentProcessUseCase.processDocument(savedDoc.getId()),
                                                        docTenantId)
                                                        .map(processResponse -> {
                                                                log.info("✓ Processamento iniciado. DocumentId: {}, Status: {}",
                                                                                savedDoc.getId(),
                                                                                processResponse.getStatus());
                                                                return UploadDocumentResponse.builder()
                                                                                .documentId(savedDoc.getId())
                                                                                .status(processResponse.getStatus()) // PROCESSING
                                                                                                                     // após
                                                                                                                     // iniciar
                                                                                                                     // processamento
                                                                                .tipoDetectado(savedDoc.getTipo())
                                                                                .build();
                                                        })
                                                        .onErrorResume(processError -> {
                                                                log.warn("⚠ Upload bem-sucedido, mas falha ao iniciar processamento: {}",
                                                                                processError.getMessage());
                                                                // Upload foi bem-sucedido, mas processamento falhou -
                                                                // retornar status PENDING
                                                                return Mono.just(UploadDocumentResponse.builder()
                                                                                .documentId(savedDoc.getId())
                                                                                .status(DocumentStatus.PENDING)
                                                                                .tipoDetectado(savedDoc.getTipo())
                                                                                .build());
                                                        });
                                });
        }

        /**
         * Extrai metadata da declaração de IR (Ano-Calendário) para salvar no
         * documento.
         * Retorna Mono.empty() se não conseguir extrair (não é crítico).
         */
        private Mono<IncomeTaxMetadata> extractIncomeTaxMetadata(InputStream inputStream) {
                return incomeTaxDeclarationService.extractIncomeTaxInfo(inputStream)
                                .map(info -> new IncomeTaxMetadata(info.getAnoCalendario()))
                                .onErrorResume(e -> {
                                        log.debug("Erro ao extrair metadata (não crítico): {}", e.getMessage());
                                        return Mono.empty();
                                });
        }

        /**
         * Calcula hash SHA-256 do arquivo.
         */
        private Mono<String> calculateFileHash(byte[] fileBytes) {
                return Mono.fromCallable(() -> {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        digest.update(fileBytes);
                        byte[] hashBytes = digest.digest();

                        StringBuilder sb = new StringBuilder();
                        for (byte b : hashBytes) {
                                sb.append(String.format("%02x", b));
                        }
                        return sb.toString();
                }).subscribeOn(Schedulers.boundedElastic());
        }

        /**
         * Classe auxiliar para metadata da declaração de IR.
         */
        private static class IncomeTaxMetadata {
                final String anoCalendario;

                IncomeTaxMetadata(String anoCalendario) {
                        this.anoCalendario = anoCalendario;
                }
        }

        /**
         * Lê o conteúdo do arquivo para um InputStream.
         */
        private Mono<InputStream> readFileContent(FilePart filePart) {
                // Escreve o conteúdo em arquivo temporário liberando cada DataBuffer conforme consumido,
                // evitando manter todos os buffers diretos (NIO/Netty) em memória ao mesmo tempo
                // (causa de OutOfMemoryError de direct buffer no upload múltiplo).
                return Mono.fromCallable(() -> java.nio.file.Files.createTempFile("irpf-upload-", ".tmp"))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(tempPath -> org.springframework.core.io.buffer.DataBufferUtils
                                                .write(filePart.content(), tempPath)
                                                .then(Mono.fromCallable(() -> {
                                                        try {
                                                                byte[] bytes = java.nio.file.Files.readAllBytes(tempPath);
                                                                return (InputStream) new java.io.ByteArrayInputStream(bytes);
                                                        } finally {
                                                                java.nio.file.Files.deleteIfExists(tempPath);
                                                        }
                                                }).subscribeOn(Schedulers.boundedElastic())));
        }

        /**
         * Upload de declaração de IR por personId - busca automaticamente CPF da pessoa
         * Processa automaticamente o documento após o upload
         */
        public Mono<UploadDocumentResponse> uploadIncomeTaxByPersonId(FilePart filePart, String personId) {
                log.info("=== INÍCIO: Upload de declaração de IR por PersonId ===");
                log.info("Arquivo: {}, PersonId: {}", filePart.filename(), personId);

                // Buscar pessoa por ID com validação de acesso
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
                                        log.info("CPF validado: {}", normalizedCpf);

                                        // Usar o tenantId da pessoa e fazer upload
                                        return ReactiveTenantContext.withTenant(
                                                        uploadIncomeTaxDeclaration(filePart, normalizedCpf),
                                                        person.getTenantId());
                                });
        }

        private int parseAnoCalendario(String anoCalendario) {
                if (anoCalendario == null) {
                        log.warn("Ano-Calendário não detectado (null). Usando ano padrão 0.");
                        return 0;
                }
                try {
                        return Integer.parseInt(anoCalendario.trim());
                } catch (NumberFormatException e) {
                        log.warn("Erro ao fazer parse do Ano-Calendário '{}'. Usando ano padrão 0.", anoCalendario);
                        return 0;
                }
        }
}
