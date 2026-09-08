package br.com.verticelabs.pdfprocessor.application.incometax;

import br.com.verticelabs.pdfprocessor.application.documents.DocumentProcessUseCase;
import br.com.verticelabs.pdfprocessor.application.persons.GetPersonByIdUseCase;
import br.com.verticelabs.pdfprocessor.domain.exceptions.DocumentoDuplicadoException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.InvalidCpfException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.InvalidPdfException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.PersonNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentStatus;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollDocument;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.domain.service.CpfValidationService;
import br.com.verticelabs.pdfprocessor.domain.service.GridFsService;
import br.com.verticelabs.pdfprocessor.domain.service.ITextIncomeTaxService;
import br.com.verticelabs.pdfprocessor.infrastructure.tenant.ReactiveTenantContext;
import br.com.verticelabs.pdfprocessor.interfaces.documents.dto.UploadDocumentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * UseCase para upload de declarações de IR usando iText 8.
 * Mesmo comportamento do IncomeTaxUploadUseCase, mas usa iText 8 para extração.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ITextIncomeTaxUploadUseCase {

        private final ITextIncomeTaxService iTextIncomeTaxService;
        private final GridFsService gridFsService;
        private final PersonRepository personRepository;
        private final PayrollDocumentRepository documentRepository;
        private final CpfValidationService cpfValidationService;
        private final DocumentProcessUseCase documentProcessUseCase;
        private final GetPersonByIdUseCase getPersonByIdUseCase;
        private final IrpfDeclaracaoDataMapper irpfDeclaracaoDataMapper;

        private static final String PDF_CONTENT_TYPE = "application/pdf";

        /**
         * Processa upload de declaração de imposto de renda usando iText 8.
         * Salva o documento e associa à pessoa pelo CPF.
         * 
         * @param filePart Arquivo PDF da declaração
         * @param cpf      CPF da pessoa
         * @return Resposta com ID do documento criado
         */
        public Mono<UploadDocumentResponse> uploadIncomeTaxDeclaration(FilePart filePart, String cpf) {
                log.info("=== INÍCIO: Upload de declaração de IR (iText 8) ===");
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
                                                                                        if (fileBytes.length == 0) {
                                                                                                log.error("Arquivo vazio detectado. Tamanho: 0 bytes");
                                                                                                return Mono.error(
                                                                                                                new InvalidPdfException(
                                                                                                                                "Arquivo vazio ou corrompido. O arquivo deve ter pelo menos 1 byte."));
                                                                                        }

                                                                                        if (fileBytes.length < 100) {
                                                                                                log.warn(
                                                                                                                "Arquivo muito pequeno. Tamanho: {} bytes. Pode estar corrompido.",
                                                                                                                fileBytes.length);
                                                                                        }

                                                                                        if (fileBytes.length > MAX_FILE_SIZE) {
                                                                                                return Mono.error(new InvalidPdfException(
                                                                                                                "Arquivo excede o limite de 10 MB."));
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
                                                                                                                                                saveIncomeTaxDocumentFast(
                                                                                                                                                                fileBytes,
                                                                                                                                                                filePart.filename(),
                                                                                                                                                                fileHash,
                                                                                                                                                                tenantId,
                                                                                                                                                                normalizedCpf,
                                                                                                                                                                person));
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

                                        if (!person.getDocumentos().contains(savedDoc.getId())) {
                                                person.getDocumentos().add(savedDoc.getId());
                                                return personRepository.save(person)
                                                                .thenReturn(savedDoc);
                                        }
                                        return Mono.just(savedDoc);
                                })
                                .map(savedDoc -> {
                                        log.info("=== UPLOAD RÁPIDO CONCLUÍDO (iText 8) ===");
                                        log.info("DocumentId: {}, Tipo: {}, Status: {}",
                                                        savedDoc.getId(), savedDoc.getTipo(), savedDoc.getStatus());

                                        String docTenantId = savedDoc.getTenantId();
                                        log.info("Disparando processamento automático do documento de IR: {}",
                                                        savedDoc.getId());
                                        ReactiveTenantContext.withTenant(
                                                        documentProcessUseCase.processDocument(savedDoc.getId()),
                                                        docTenantId
                                        ).subscribe(
                                                        processResponse -> log.info(
                                                                        "✓ Processamento iniciado. DocumentId: {}, Status: {}",
                                                                        savedDoc.getId(), processResponse.getStatus()),
                                                        processError -> log.warn(
                                                                        "⚠ Upload bem-sucedido, mas falha ao iniciar processamento: {}",
                                                                        processError.getMessage()));

                                        return UploadDocumentResponse.builder()
                                                        .documentId(savedDoc.getId())
                                                        .status(DocumentStatus.PROCESSING)
                                                        .tipoDetectado(savedDoc.getTipo())
                                                        .build();
                                });
        }

        private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

        private Mono<UploadDocumentResponse> saveIncomeTaxDocumentFast(
                        byte[] fileBytes,
                        String filename,
                        String fileHash,
                        String tenantId,
                        String normalizedCpf,
                        Person person) {
                return gridFsService.storeFileWithHash(
                                new ByteArrayInputStream(fileBytes),
                                filename,
                                PDF_CONTENT_TYPE,
                                fileHash)
                                .flatMap(fileId -> {
                                        log.info("Arquivo IR salvo no GridFS com ID: {} (upload rápido)", fileId);
                                        PayrollDocument document = PayrollDocument.builder()
                                                        .tenantId(tenantId)
                                                        .cpf(normalizedCpf)
                                                        .tipo(DocumentType.INCOME_TAX)
                                                        .status(DocumentStatus.PENDING)
                                                        .originalFileId(fileId)
                                                        .fileHash(fileHash)
                                                        .dataUpload(Instant.now())
                                                        .build();
                                        return saveDocumentAndUpdatePerson(document, person);
                                });
        }

        /**
         * Extrai metadata da declaração de IR usando iText 8.
         * Retorna Mono.empty() se não conseguir extrair (não é crítico).
         * @deprecated Extração ocorre no processamento assíncrono ({@link br.com.verticelabs.pdfprocessor.application.documents.DocumentProcessUseCase}).
         */
        @Deprecated
        private Mono<IncomeTaxMetadata> extractIncomeTaxMetadata(InputStream inputStream) {
                return iTextIncomeTaxService.extractIncomeTaxInfo(inputStream)
                                .map(info -> {
                                        IrpfDeclaracaoData irpfData = irpfDeclaracaoDataMapper.fromIncomeTaxInfo(info);
                                        return new IncomeTaxMetadata(info.getAnoCalendario(), info.getCpf(), irpfData);
                                })
                                .doOnSuccess(metadata -> log.info(
                                                "📄 Metadata extraída via iText 8: Ano-Calendário = {}, CPF = {}, "
                                                + "Dependentes = {}, Fontes = {}, Controle = {}",
                                                metadata != null ? metadata.anoCalendario : "null",
                                                metadata != null ? metadata.cpf : "null",
                                                metadata != null && metadata.irpfData != null ? metadata.irpfData.getDependentes().size() : 0,
                                                metadata != null && metadata.irpfData != null ? metadata.irpfData.getRendimentosFontesTitular().size() : 0,
                                                metadata != null && metadata.irpfData != null ? metadata.irpfData.getControle() : "null"))
                                .onErrorResume(e -> {
                                        log.warn("⚠️ Erro ao extrair metadata via iText 8: {}", e.getMessage());
                                        return Mono.empty();
                                });
        }

        /**
         * Normaliza CPF removendo pontos e traço para comparação.
         * @deprecated Validação de CPF ocorre no processamento assíncrono.
         */
        @Deprecated
        private String normalizeCpfForComparison(String cpf) {
                if (cpf == null) return null;
                return cpf.replaceAll("[^\\d]", "");
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
                final String cpf;
                final IrpfDeclaracaoData irpfData;

                IncomeTaxMetadata(String anoCalendario, String cpf, IrpfDeclaracaoData irpfData) {
                        this.anoCalendario = anoCalendario;
                        this.cpf = cpf;
                        this.irpfData = irpfData;
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
                                                                return (InputStream) new ByteArrayInputStream(bytes);
                                                        } finally {
                                                                java.nio.file.Files.deleteIfExists(tempPath);
                                                        }
                                                }).subscribeOn(Schedulers.boundedElastic())));
        }

        /**
         * Upload de declaração de IR por personId - busca automaticamente CPF da
         * pessoa.
         * Processa automaticamente o documento após o upload.
         */
        public Mono<UploadDocumentResponse> uploadIncomeTaxByPersonId(FilePart filePart, String personId) {
                log.info("=== INÍCIO: Upload de declaração de IR por PersonId (iText 8) ===");
                log.info("Arquivo: {}, PersonId: {}", filePart.filename(), personId);

                return getPersonByIdUseCase.execute(personId)
                                .flatMap(person -> {
                                        log.info("✓ Pessoa encontrada: CPF={}, Nome={}, TenantId={}",
                                                        person.getCpf(), person.getNome(), person.getTenantId());

                                        String normalizedCpf = cpfValidationService.normalize(person.getCpf());
                                        if (!cpfValidationService.isValid(normalizedCpf)) {
                                                log.error("CPF inválido: {}", person.getCpf());
                                                return Mono.error(new InvalidCpfException(
                                                                "CPF inválido: " + person.getCpf()));
                                        }
                                        log.info("CPF validado: {}", normalizedCpf);

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
