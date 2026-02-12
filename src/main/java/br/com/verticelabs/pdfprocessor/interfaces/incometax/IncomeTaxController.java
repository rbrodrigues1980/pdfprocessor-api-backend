package br.com.verticelabs.pdfprocessor.interfaces.incometax;

import br.com.verticelabs.pdfprocessor.application.incometax.ITextIncomeTaxUploadUseCase;
import br.com.verticelabs.pdfprocessor.domain.service.ITextIncomeTaxService;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService;
import br.com.verticelabs.pdfprocessor.interfaces.documents.dto.UploadDocumentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;

/**
 * Controller REST para extração de declarações de Imposto de Renda usando iText
 * 8.
 * Oferece extração mais precisa que a API existente, especialmente para PDFs
 * com layouts complexos.
 */
@Slf4j
@RestController
@RequestMapping("/incometax")
@RequiredArgsConstructor
@Tag(name = "Income Tax - iText 8", description = "API para extração de declarações de IR usando iText 8")
public class IncomeTaxController {

    private final ITextIncomeTaxService iTextIncomeTaxService;
    private final ITextIncomeTaxUploadUseCase iTextIncomeTaxUploadUseCase;

    // ==========================================
    // ENDPOINTS DE UPLOAD (com persistência)
    // ==========================================

    /**
     * Upload de declaração de IR com persistência.
     * Salva o documento no banco e associa à pessoa pelo CPF.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload de declaração de IR", description = "Faz upload de um PDF de declaração de IR, extrai as informações usando iText 8, "
            +
            "salva no banco de dados e associa à pessoa pelo CPF. " +
            "Comportamento idêntico ao endpoint antigo, mas com extração iText 8.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Documento criado com sucesso", content = @Content(schema = @Schema(implementation = UploadDocumentResponse.class))),
            @ApiResponse(responseCode = "400", description = "CPF inválido ou pessoa não encontrada"),
            @ApiResponse(responseCode = "409", description = "Documento duplicado")
    })
    public Mono<ResponseEntity<UploadDocumentResponse>> uploadIncomeTax(
            @Parameter(description = "Arquivo PDF da declaração de IR") @RequestPart("file") FilePart filePart,
            @Parameter(description = "CPF da pessoa (formato: 000.000.000-00 ou 00000000000)") @RequestPart("cpf") String cpf) {

        log.debug("📥 Upload de declaração de IR (iText 8): arquivo={}, cpf={}", filePart.filename(), cpf);

        return iTextIncomeTaxUploadUseCase.uploadIncomeTaxDeclaration(filePart, cpf)
                .<ResponseEntity<UploadDocumentResponse>>map(response -> {
                    log.debug("✅ Upload concluído: documentId={}, status={}",
                            response.getDocumentId(), response.getStatus());
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                })
                .onErrorResume(e -> {
                    log.error("❌ Erro no upload: {}", e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(
                            UploadDocumentResponse.builder()
                                    .status(null)
                                    .tipoDetectado(null)
                                    .build()));
                });
    }

    /**
     * Upload de declaração de IR por personId.
     * Busca automaticamente o CPF da pessoa.
     */
    @PostMapping(value = "/upload/person/{personId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload de declaração de IR por PersonId", description = "Faz upload de um PDF de declaração de IR para uma pessoa específica. "
            +
            "Busca automaticamente o CPF da pessoa pelo ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Documento criado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada"),
            @ApiResponse(responseCode = "409", description = "Documento duplicado")
    })
    public Mono<ResponseEntity<UploadDocumentResponse>> uploadIncomeTaxByPersonId(
            @Parameter(description = "Arquivo PDF da declaração de IR") @RequestPart("file") FilePart filePart,
            @Parameter(description = "ID da pessoa") @PathVariable String personId) {

        log.debug("📥 Upload de declaração de IR por PersonId (iText 8): arquivo={}, personId={}",
                filePart.filename(), personId);

        return iTextIncomeTaxUploadUseCase.uploadIncomeTaxByPersonId(filePart, personId)
                .<ResponseEntity<UploadDocumentResponse>>map(response -> {
                    log.debug("✅ Upload concluído: documentId={}, status={}",
                            response.getDocumentId(), response.getStatus());
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                })
                .onErrorResume(e -> {
                    log.error("❌ Erro no upload: {}", e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(
                            UploadDocumentResponse.builder()
                                    .status(null)
                                    .tipoDetectado(null)
                                    .build()));
                });
    }

    // ==========================================
    // ENDPOINTS DE EXTRAÇÃO (apenas leitura)
    // ==========================================
    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Extrai informações de declaração de IR", description = "Processa um PDF de declaração de Imposto de Renda e extrai todas as 37 rubricas documentadas. "
            +
            "Usa iText 8 para extração mais precisa de PDFs com layouts complexos (duas colunas).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Extração realizada com sucesso", content = @Content(schema = @Schema(implementation = IncomeTaxExtractionResponse.class))),
            @ApiResponse(responseCode = "400", description = "PDF inválido ou página RESUMO não encontrada"),
            @ApiResponse(responseCode = "500", description = "Erro interno no processamento")
    })
    public Mono<ResponseEntity<IncomeTaxExtractionResponse>> extractIncomeTax(
            @Parameter(description = "Arquivo PDF da declaração de IR") @RequestPart("file") FilePart filePart) {

        long startTime = System.currentTimeMillis();
        String filename = filePart.filename();
        log.debug("📥 Recebendo PDF para extração: {}", filename);

        return DataBufferUtils.join(filePart.content())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    return iTextIncomeTaxService.extractIncomeTaxInfo(new ByteArrayInputStream(bytes))
                            .map(info -> {
                                long elapsedTime = System.currentTimeMillis() - startTime;
                                log.debug("✅ Extração concluída em {}ms para arquivo: {}", elapsedTime, filename);

                                return ResponseEntity.ok(new IncomeTaxExtractionResponse(
                                        true,
                                        "Extração realizada com sucesso",
                                        filename,
                                        mapToInfoDto(info),
                                        null,
                                        elapsedTime));
                            });
                })
                .onErrorResume(e -> {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    log.error("❌ Erro na extração: {}", e.getMessage(), e);

                    return Mono.just(ResponseEntity.badRequest().body(new IncomeTaxExtractionResponse(
                            false,
                            "Erro na extração: " + e.getMessage(),
                            filename,
                            null,
                            null,
                            elapsedTime)));
                });
    }

    /**
     * Extrai o texto bruto de todas as páginas do PDF (para debug).
     */
    @PostMapping(value = "/extract/raw", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Extrai texto bruto do PDF", description = "Retorna o texto bruto extraído de todas as páginas do PDF. Útil para debug e análise.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Texto extraído com sucesso"),
            @ApiResponse(responseCode = "400", description = "PDF inválido")
    })
    public Mono<ResponseEntity<RawTextResponse>> extractRawText(
            @Parameter(description = "Arquivo PDF") @RequestPart("file") FilePart filePart) {

        long startTime = System.currentTimeMillis();
        String filename = filePart.filename();

        return DataBufferUtils.join(filePart.content())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    return iTextIncomeTaxService.extractRawText(new ByteArrayInputStream(bytes))
                            .map(rawText -> {
                                long elapsedTime = System.currentTimeMillis() - startTime;
                                return ResponseEntity.ok(new RawTextResponse(
                                        true,
                                        filename,
                                        rawText,
                                        rawText.length(),
                                        elapsedTime));
                            });
                })
                .onErrorResume(e -> {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    return Mono.just(ResponseEntity.badRequest().body(new RawTextResponse(
                            false,
                            filename,
                            "Erro: " + e.getMessage(),
                            0,
                            elapsedTime)));
                });
    }

    /**
     * Extrai texto bruto de uma página específica.
     */
    @PostMapping(value = "/extract/page/{pageNumber}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Extrai texto de uma página específica", description = "Retorna o texto bruto de uma página específica do PDF (1-indexed).")
    public Mono<ResponseEntity<RawTextResponse>> extractRawTextFromPage(
            @Parameter(description = "Arquivo PDF") @RequestPart("file") FilePart filePart,
            @Parameter(description = "Número da página (1-indexed)") @PathVariable int pageNumber) {

        long startTime = System.currentTimeMillis();
        String filename = filePart.filename();

        return DataBufferUtils.join(filePart.content())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    return iTextIncomeTaxService.extractRawTextFromPage(new ByteArrayInputStream(bytes), pageNumber)
                            .map(rawText -> {
                                long elapsedTime = System.currentTimeMillis() - startTime;
                                return ResponseEntity.ok(new RawTextResponse(
                                        true,
                                        filename + " (Página " + pageNumber + ")",
                                        rawText,
                                        rawText.length(),
                                        elapsedTime));
                            });
                })
                .onErrorResume(e -> {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    return Mono.just(ResponseEntity.badRequest().body(new RawTextResponse(
                            false,
                            filename,
                            "Erro: " + e.getMessage(),
                            0,
                            elapsedTime)));
                });
    }

    /**
     * Extrai informações completas + texto bruto (para debug completo).
     */
    @PostMapping(value = "/extract/debug", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Extração com debug completo", description = "Retorna as informações extraídas E o texto bruto da página RESUMO. Útil para diagnóstico.")
    public Mono<ResponseEntity<IncomeTaxExtractionResponse>> extractWithDebug(
            @Parameter(description = "Arquivo PDF da declaração de IR") @RequestPart("file") FilePart filePart) {

        long startTime = System.currentTimeMillis();
        String filename = filePart.filename();
        log.debug("🔍 Extração DEBUG para: {}", filename);

        return DataBufferUtils.join(filePart.content())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    // Primeiro encontra a página RESUMO
                    return iTextIncomeTaxService.findResumoPage(new ByteArrayInputStream(bytes))
                            .flatMap(resumoPage -> {
                                // Busca o texto da página RESUMO
                                return iTextIncomeTaxService.extractRawTextFromPage(
                                        new ByteArrayInputStream(bytes), resumoPage)
                                        .flatMap(resumoText -> {
                                            // Extrai as informações
                                            return iTextIncomeTaxService.extractIncomeTaxInfo(
                                                    new ByteArrayInputStream(bytes))
                                                    .map(info -> {
                                                        long elapsedTime = System.currentTimeMillis() - startTime;
                                                        log.debug("✅ Extração DEBUG concluída em {}ms", elapsedTime);

                                                        return ResponseEntity.ok(new IncomeTaxExtractionResponse(
                                                                true,
                                                                "Página RESUMO: " + resumoPage,
                                                                filename,
                                                                mapToInfoDto(info),
                                                                resumoText,
                                                                elapsedTime));
                                                    });
                                        });
                            });
                })
                .onErrorResume(e -> {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    log.error("❌ Erro na extração DEBUG: {}", e.getMessage(), e);

                    return Mono.just(ResponseEntity.badRequest().body(new IncomeTaxExtractionResponse(
                            false,
                            "Erro: " + e.getMessage(),
                            filename,
                            null,
                            null,
                            elapsedTime)));
                });
    }

    /**
     * Encontra a página RESUMO no PDF.
     */
    @PostMapping(value = "/find-resumo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Encontra a página RESUMO", description = "Retorna o número da página que contém 'RESUMO' no PDF.")
    public Mono<ResponseEntity<ResumoPageResponse>> findResumoPage(
            @Parameter(description = "Arquivo PDF") @RequestPart("file") FilePart filePart) {

        String filename = filePart.filename();

        return DataBufferUtils.join(filePart.content())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    return iTextIncomeTaxService.findResumoPage(new ByteArrayInputStream(bytes))
                            .map(pageNumber -> ResponseEntity.ok(new ResumoPageResponse(
                                    true,
                                    filename,
                                    pageNumber,
                                    "Página RESUMO encontrada")));
                })
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body(new ResumoPageResponse(
                        false,
                        filename,
                        null,
                        "Erro: " + e.getMessage()))));
    }

    // ==========================================
    // DTOs
    // ==========================================

    /**
     * Mapeia IncomeTaxInfo para o DTO de resposta.
     */
    private IncomeTaxInfoDto mapToInfoDto(IncomeTaxDeclarationService.IncomeTaxInfo info) {
        return new IncomeTaxInfoDto(
                // Dados Básicos
                info.getNome(),
                info.getCpf(),
                info.getAnoCalendario(),
                info.getExercicio(),
                // IMPOSTO DEVIDO
                info.getBaseCalculoImposto(),
                info.getImpostoDevido(),
                info.getDeducaoIncentivo(),
                info.getImpostoDevidoI(),
                info.getContribuicaoPrevEmpregadorDomestico(),
                info.getImpostoDevidoII(),
                info.getImpostoDevidoRRA(),
                info.getTotalImpostoDevido(),
                info.getSaldoImpostoPagar(),
                // Rendimentos e Deduções
                info.getRendimentosTributaveis(),
                info.getDeducoes(),
                info.getImpostoRetidoFonteTitular(),
                info.getImpostoPagoTotal(),
                info.getImpostoRestituir(),
                // DEDUÇÕES Individuais
                info.getDeducoesContribPrevOficial(),
                info.getDeducoesContribPrevRRA(),
                info.getDeducoesContribPrevCompl(),
                info.getDeducoesDependentes(),
                info.getDeducoesInstrucao(),
                info.getDeducoesMedicas(),
                info.getDeducoesPensaoJudicial(),
                info.getDeducoesPensaoEscritura(),
                info.getDeducoesPensaoRRA(),
                info.getDeducoesLivroCaixa(),
                // IMPOSTO PAGO Individuais
                info.getImpostoRetidoFonteDependentes(),
                info.getCarneLeaoTitular(),
                info.getCarneLeaoDependentes(),
                info.getImpostoComplementar(),
                info.getImpostoPagoExterior(),
                info.getImpostoRetidoFonteLei11033(),
                info.getImpostoRetidoRRA(),
                // Campos 2017+
                info.getDescontoSimplificado(),
                info.getAliquotaEfetiva());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Resposta da extração de declaração de IR")
    public static class IncomeTaxExtractionResponse {
        @Schema(description = "Indica se a extração foi bem-sucedida")
        private boolean success;

        @Schema(description = "Mensagem de status ou erro")
        private String message;

        @Schema(description = "Nome do arquivo processado")
        private String filename;

        @Schema(description = "Informações extraídas da declaração")
        private IncomeTaxInfoDto data;

        @Schema(description = "Texto bruto da página RESUMO (apenas em modo debug)")
        private String rawText;

        @Schema(description = "Tempo de processamento em milissegundos")
        private long extractionTimeMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Informações extraídas da declaração de IR")
    public static class IncomeTaxInfoDto {
        // Dados Básicos
        @Schema(description = "Nome do contribuinte")
        private String nome;
        @Schema(description = "CPF do contribuinte")
        private String cpf;
        @Schema(description = "Ano-calendário (ex: 2017)")
        private String anoCalendario;
        @Schema(description = "Exercício fiscal (ex: 2018)")
        private String exercicio;

        // IMPOSTO DEVIDO
        @Schema(description = "Base de cálculo do imposto")
        private BigDecimal baseCalculoImposto;
        @Schema(description = "Imposto devido")
        private BigDecimal impostoDevido;
        @Schema(description = "Dedução de incentivo")
        private BigDecimal deducaoIncentivo;
        @Schema(description = "Imposto devido I")
        private BigDecimal impostoDevidoI;
        @Schema(description = "Contribuição Prev. Empregador Doméstico")
        private BigDecimal contribuicaoPrevEmpregadorDomestico;
        @Schema(description = "Imposto devido II")
        private BigDecimal impostoDevidoII;
        @Schema(description = "Imposto devido RRA")
        private BigDecimal impostoDevidoRRA;
        @Schema(description = "Total do imposto devido")
        private BigDecimal totalImpostoDevido;
        @Schema(description = "Saldo de imposto a pagar")
        private BigDecimal saldoImpostoPagar;

        // Rendimentos e Deduções
        @Schema(description = "Total de rendimentos tributáveis")
        private BigDecimal rendimentosTributaveis;
        @Schema(description = "Total de deduções")
        private BigDecimal deducoes;
        @Schema(description = "Imposto retido na fonte do titular")
        private BigDecimal impostoRetidoFonteTitular;
        @Schema(description = "Total do imposto pago")
        private BigDecimal impostoPagoTotal;
        @Schema(description = "Imposto a restituir")
        private BigDecimal impostoRestituir;

        // DEDUÇÕES Individuais
        @Schema(description = "Contribuição à previdência oficial")
        private BigDecimal deducoesContribPrevOficial;
        @Schema(description = "Contribuição à previdência oficial (RRA)")
        private BigDecimal deducoesContribPrevRRA;
        @Schema(description = "Contribuição à previdência complementar/privada")
        private BigDecimal deducoesContribPrevCompl;
        @Schema(description = "Dependentes")
        private BigDecimal deducoesDependentes;
        @Schema(description = "Despesas com instrução")
        private BigDecimal deducoesInstrucao;
        @Schema(description = "Despesas médicas")
        private BigDecimal deducoesMedicas;
        @Schema(description = "Pensão alimentícia judicial")
        private BigDecimal deducoesPensaoJudicial;
        @Schema(description = "Pensão alimentícia por escritura pública")
        private BigDecimal deducoesPensaoEscritura;
        @Schema(description = "Pensão alimentícia judicial (RRA)")
        private BigDecimal deducoesPensaoRRA;
        @Schema(description = "Livro caixa")
        private BigDecimal deducoesLivroCaixa;

        // IMPOSTO PAGO Individuais
        @Schema(description = "Imposto retido na fonte dos dependentes")
        private BigDecimal impostoRetidoFonteDependentes;
        @Schema(description = "Carnê-Leão do titular")
        private BigDecimal carneLeaoTitular;
        @Schema(description = "Carnê-Leão dos dependentes")
        private BigDecimal carneLeaoDependentes;
        @Schema(description = "Imposto complementar")
        private BigDecimal impostoComplementar;
        @Schema(description = "Imposto pago no exterior")
        private BigDecimal impostoPagoExterior;
        @Schema(description = "Imposto retido na fonte (Lei 11.033/2004)")
        private BigDecimal impostoRetidoFonteLei11033;
        @Schema(description = "Imposto retido RRA")
        private BigDecimal impostoRetidoRRA;

        // Campos 2017+
        @Schema(description = "Desconto simplificado (2017+)")
        private BigDecimal descontoSimplificado;
        @Schema(description = "Alíquota efetiva % (2017+)")
        private BigDecimal aliquotaEfetiva;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Resposta com texto bruto extraído")
    public static class RawTextResponse {
        @Schema(description = "Indica se a extração foi bem-sucedida")
        private boolean success;

        @Schema(description = "Nome do arquivo")
        private String filename;

        @Schema(description = "Texto bruto extraído")
        private String rawText;

        @Schema(description = "Número de caracteres extraídos")
        private int characterCount;

        @Schema(description = "Tempo de processamento em milissegundos")
        private long extractionTimeMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Resposta com número da página RESUMO")
    public static class ResumoPageResponse {
        @Schema(description = "Indica se a busca foi bem-sucedida")
        private boolean success;

        @Schema(description = "Nome do arquivo")
        private String filename;

        @Schema(description = "Número da página RESUMO (1-indexed)")
        private Integer pageNumber;

        @Schema(description = "Mensagem de status")
        private String message;
    }
}
