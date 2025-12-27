package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.domain.service.ITextIncomeTaxService;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementação do serviço de extração de declarações de IR usando iText 8.
 * Usa LocationTextExtractionStrategy para melhor extração de PDFs com layouts
 * complexos.
 */
@Slf4j
@Service
public class ITextIncomeTaxServiceImpl implements ITextIncomeTaxService {

    // ==========================================
    // PADRÕES REGEX PARA EXTRAÇÃO
    // ==========================================

    // Dados Básicos
    private static final Pattern ANO_CALENDARIO_PATTERN = Pattern.compile(
            "(?i)ano[\\s.-]*calend[aá]rio[\\s:]*([\\d]{4})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EXERCICIO_PATTERN = Pattern.compile(
            "(?i)exerc[ií]cio[\\s:]*([\\d]{4})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NOME_PATTERN = Pattern.compile(
            "(?i)nome[\\s:]+([A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ][A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇa-záéíóúàèìòùâêîôûãõç\\s]+?)(?=\\s*(?:CPF|\\d{3}\\.\\d{3}|$))",
            Pattern.MULTILINE);

    private static final Pattern CPF_PATTERN = Pattern.compile(
            "(\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2})",
            Pattern.CASE_INSENSITIVE);

    // IMPOSTO DEVIDO
    private static final Pattern BASE_CALCULO_IMPOSTO_PATTERN = Pattern.compile(
            "(?i)base\\s+de\\s+c[aá]lculo\\s+do\\s+imposto[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IMPOSTO_DEVIDO_PATTERN = Pattern.compile(
            "(?i)imposto\\s+devido(?![\\s]*(I|II|RRA))[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DEDUCAO_INCENTIVO_PATTERN = Pattern.compile(
            "(?i)dedu[çc][ãa]o\\s+de\\s+incentivo[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IMPOSTO_DEVIDO_I_PATTERN = Pattern.compile(
            "(?i)imposto\\s+devido\\s+I(?![I])[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CONTRIBUICAO_PREV_EMPREGADOR_DOMESTICO_PATTERN = Pattern.compile(
            "(?i)contribui[çc][ãa]o\\s+prev[\\s\\S]*?empregador\\s+dom[eé]stico[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IMPOSTO_DEVIDO_II_PATTERN = Pattern.compile(
            "(?i)imposto\\s+devido\\s+II[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IMPOSTO_DEVIDO_RRA_PATTERN = Pattern.compile(
            "(?i)imposto\\s+devido\\s+RRA[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TOTAL_IMPOSTO_DEVIDO_PATTERN = Pattern.compile(
            "(?i)total\\s+do\\s+imposto\\s+devido[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SALDO_IMPOSTO_PAGAR_PATTERN = Pattern.compile(
            "(?i)saldo\\s+(?:de\\s+)?imposto\\s+a\\s+pagar[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    // Rendimentos e Deduções
    // Padrão para capturar o TOTAL de RENDIMENTOS TRIBUTÁVEIS
    // Texto: "TOTAL\r\n168.097,04" após a seção RENDIMENTOS TRIBUTÁVEIS
    private static final Pattern RENDIMENTOS_TRIBUTAVEIS_TOTAL_PATTERN = Pattern.compile(
            "(?i)RENDIMENTOS\\s+TRIBUT[AÁ]VEIS[\\s\\S]*?TOTAL[\\r\\n\\s]+([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    // Padrão alternativo: buscar pela última ocorrência de TOTAL na seção
    private static final Pattern RENDIMENTOS_TRIBUTAVEIS_PATTERN = Pattern.compile(
            "(?i)(?:total\\s+de\\s+)?rendimentos\\s+tribut[aá]veis[^\\d]*([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    // Padrão para DEDUÇÕES TOTAL - buscar "TOTAL" seguido de valor após seção
    // DEDUÇÕES
    private static final Pattern DEDUCOES_TOTAL_PATTERN = Pattern.compile(
            "(?i)DEDU[ÇC][ÕO]ES[\\s\\S]*?TOTAL[\\r\\n\\s]+([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IMPOSTO_RETIDO_FONTE_TITULAR_PATTERN = Pattern.compile(
            "(?i)imposto\\s+retido\\s+na\\s+fonte\\s+do\\s+titular[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IMPOSTO_PAGO_TOTAL_PATTERN = Pattern.compile(
            "(?i)total\\s+do\\s+imposto\\s+pago[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IMPOSTO_RESTITUIR_PATTERN = Pattern.compile(
            "(?i)imposto\\s+a\\s+restituir[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    // DEDUÇÕES Individuais
    private static final Pattern DEDUCOES_CONTRIB_PREV_OFICIAL_PATTERN = Pattern.compile(
            "(?i)contribui[çc][ãa]o\\s+[àa]\\s+previd[êe]ncia\\s+oficial[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DEDUCOES_CONTRIB_PREV_RRA_PATTERN = Pattern.compile(
            "(?i)contribui[çc][ãa]o\\s+[àa]\\s+previd[êe]ncia\\s+oficial\\s*\\(?RRA\\)?[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    // Padrão para "Contribuição à previdência complementar, pública (acima do
    // limite do patrocinador) ou privada, e Fapi."
    private static final Pattern DEDUCOES_CONTRIB_PREV_COMPL_PATTERN = Pattern.compile(
            "(?i)contribui[çc][ãa]o\\s+[àa]\\s+previd[êe]ncia\\s+complementar[^\\d]*([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DEDUCOES_DEPENDENTES_PATTERN = Pattern.compile(
            "(?i)dependentes[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DEDUCOES_INSTRUCAO_PATTERN = Pattern.compile(
            "(?i)despesas\\s+com\\s+instru[çc][ãa]o[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DEDUCOES_MEDICAS_PATTERN = Pattern.compile(
            "(?i)despesas\\s+m[eé]dicas[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DEDUCOES_PENSAO_JUDICIAL_PATTERN = Pattern.compile(
            "(?i)pens[ãa]o\\s+aliment[íi]cia\\s+judicial(?!\\s*\\()?[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DEDUCOES_PENSAO_ESCRITURA_PATTERN = Pattern.compile(
            "(?i)pens[ãa]o\\s+aliment[íi]cia\\s+por\\s+escritura[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DEDUCOES_PENSAO_RRA_PATTERN = Pattern.compile(
            "(?i)pens[ãa]o\\s+aliment[íi]cia\\s+judicial\\s*\\(?RRA\\)?[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DEDUCOES_LIVRO_CAIXA_PATTERN = Pattern.compile(
            "(?i)livro\\s+caixa[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    // IMPOSTO PAGO Individuais
    private static final Pattern IMPOSTO_RETIDO_FONTE_DEPENDENTES_PATTERN = Pattern.compile(
            "(?i)imp\\.?\\s+retido\\s+na\\s+fonte\\s+dos\\s+dependentes[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CARNE_LEAO_TITULAR_PATTERN = Pattern.compile(
            "(?i)carn[êe]-?le[ãa]o\\s+do\\s+titular[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CARNE_LEAO_DEPENDENTES_PATTERN = Pattern.compile(
            "(?i)carn[êe]-?le[ãa]o\\s+dos\\s+dependentes[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IMPOSTO_COMPLEMENTAR_PATTERN = Pattern.compile(
            "(?i)imposto\\s+complementar[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IMPOSTO_PAGO_EXTERIOR_PATTERN = Pattern.compile(
            "(?i)imposto\\s+pago\\s+no\\s+exterior[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IMPOSTO_RETIDO_FONTE_LEI_11033_PATTERN = Pattern.compile(
            "(?i)imposto\\s+retido\\s+na\\s+fonte\\s*\\(?Lei[\\s\\S]*?11\\.?033[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IMPOSTO_RETIDO_RRA_PATTERN = Pattern.compile(
            "(?i)imposto\\s+retido\\s+RRA[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    // Campos 2017+ (Desconto Simplificado)
    private static final Pattern DESCONTO_SIMPLIFICADO_PATTERN = Pattern.compile(
            "(?i)desconto\\s+simplificado[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ALIQUOTA_EFETIVA_PATTERN = Pattern.compile(
            "(?i)al[ií]quota\\s+efetiva[\\s\\S]*?([\\d]{1,3},\\d{2})",
            Pattern.CASE_INSENSITIVE);

    @Override
    public Mono<IncomeTaxDeclarationService.IncomeTaxInfo> extractIncomeTaxInfo(InputStream inputStream) {
        log.info("🚀 Iniciando extração de IR com iText 8");

        return Mono.fromCallable(() -> {
            byte[] pdfBytes = inputStream.readAllBytes();
            inputStream.close();
            return pdfBytes;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(pdfBytes -> {
                    return findResumoPage(new ByteArrayInputStream(pdfBytes))
                            .flatMap(resumoPage -> {
                                log.info("📄 Página RESUMO encontrada: {}", resumoPage);

                                return Mono.zip(
                                        extractRawTextFromPage(new ByteArrayInputStream(pdfBytes), 1),
                                        extractRawTextFromPage(new ByteArrayInputStream(pdfBytes), resumoPage))
                                        .map(tuple -> {
                                            String primeiraPageText = tuple.getT1();
                                            String resumoPageText = tuple.getT2();

                                            log.debug("📝 Texto primeira página (primeiros 500 chars): {}",
                                                    primeiraPageText.substring(0,
                                                            Math.min(500, primeiraPageText.length())));
                                            log.debug("📝 Texto página RESUMO (primeiros 500 chars): {}",
                                                    resumoPageText.substring(0,
                                                            Math.min(500, resumoPageText.length())));

                                            return parseIncomeTaxInfo(primeiraPageText, resumoPageText);
                                        });
                            });
                })
                .doOnSuccess(info -> log.info("✅ Extração concluída com sucesso"))
                .doOnError(e -> log.error("❌ Erro na extração: {}", e.getMessage(), e));
    }

    @Override
    public Mono<String> extractRawText(InputStream inputStream) {
        return Mono.fromCallable(() -> {
            StringBuilder fullText = new StringBuilder();

            try (PdfReader reader = new PdfReader(inputStream);
                    PdfDocument pdfDoc = new PdfDocument(reader)) {

                int totalPages = pdfDoc.getNumberOfPages();
                log.info("📄 PDF tem {} páginas", totalPages);

                for (int i = 1; i <= totalPages; i++) {
                    LocationTextExtractionStrategy strategy = new LocationTextExtractionStrategy();
                    String pageText = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i), strategy);
                    fullText.append("=== PÁGINA ").append(i).append(" ===\n");
                    fullText.append(pageText).append("\n\n");
                }
            }

            return fullText.toString();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<String> extractRawTextFromPage(InputStream inputStream, int pageNumber) {
        return Mono.fromCallable(() -> {
            try (PdfReader reader = new PdfReader(inputStream);
                    PdfDocument pdfDoc = new PdfDocument(reader)) {

                if (pageNumber < 1 || pageNumber > pdfDoc.getNumberOfPages()) {
                    throw new IllegalArgumentException(
                            "Página " + pageNumber + " inválida. PDF tem " + pdfDoc.getNumberOfPages() + " páginas.");
                }

                LocationTextExtractionStrategy strategy = new LocationTextExtractionStrategy();
                return PdfTextExtractor.getTextFromPage(pdfDoc.getPage(pageNumber), strategy);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Integer> findResumoPage(InputStream inputStream) {
        return Mono.fromCallable(() -> {
            try (PdfReader reader = new PdfReader(inputStream);
                    PdfDocument pdfDoc = new PdfDocument(reader)) {

                int totalPages = pdfDoc.getNumberOfPages();

                for (int i = 1; i <= totalPages; i++) {
                    LocationTextExtractionStrategy strategy = new LocationTextExtractionStrategy();
                    String pageText = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i), strategy);

                    if (pageText != null && pageText.toUpperCase().contains("RESUMO")) {
                        log.debug("🔍 'RESUMO' encontrado na página {}", i);
                        return i;
                    }
                }

                throw new IllegalArgumentException("Página RESUMO não encontrada no PDF");
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Faz o parsing das informações de IR a partir do texto extraído.
     */
    private IncomeTaxDeclarationService.IncomeTaxInfo parseIncomeTaxInfo(String primeiraPageText,
            String resumoPageText) {
        String combinedText = primeiraPageText + "\n" + resumoPageText;

        // Dados Básicos
        String nome = extractString(combinedText, NOME_PATTERN);
        String cpf = extractString(combinedText, CPF_PATTERN);
        String anoCalendario = extractString(resumoPageText, ANO_CALENDARIO_PATTERN);
        if (anoCalendario == null) {
            anoCalendario = extractString(primeiraPageText, ANO_CALENDARIO_PATTERN);
        }
        String exercicio = extractString(resumoPageText, EXERCICIO_PATTERN);
        if (exercicio == null) {
            exercicio = extractString(primeiraPageText, EXERCICIO_PATTERN);
        }

        log.info("📌 Dados Básicos - Nome: {}, CPF: {}, Exercício: {}, Ano-Calendário: {}",
                nome, cpf, exercicio, anoCalendario);

        // IMPOSTO DEVIDO
        BigDecimal baseCalculoImposto = extractValorMonetario(resumoPageText, BASE_CALCULO_IMPOSTO_PATTERN);
        BigDecimal impostoDevido = extractValorMonetario(resumoPageText, IMPOSTO_DEVIDO_PATTERN);
        BigDecimal deducaoIncentivo = extractValorMonetario(resumoPageText, DEDUCAO_INCENTIVO_PATTERN);
        BigDecimal impostoDevidoI = extractValorMonetario(resumoPageText, IMPOSTO_DEVIDO_I_PATTERN);
        BigDecimal contribuicaoPrevEmpregadorDomestico = extractValorMonetario(resumoPageText,
                CONTRIBUICAO_PREV_EMPREGADOR_DOMESTICO_PATTERN);
        BigDecimal impostoDevidoII = extractValorMonetario(resumoPageText, IMPOSTO_DEVIDO_II_PATTERN);
        BigDecimal impostoDevidoRRA = extractValorMonetario(resumoPageText, IMPOSTO_DEVIDO_RRA_PATTERN);
        BigDecimal totalImpostoDevido = extractValorMonetario(resumoPageText, TOTAL_IMPOSTO_DEVIDO_PATTERN);
        BigDecimal saldoImpostoPagar = extractValorMonetario(resumoPageText, SALDO_IMPOSTO_PAGAR_PATTERN);

        // Fallback: usar Total se Imposto Devido simples não foi encontrado
        if ((impostoDevido == null || impostoDevido.compareTo(BigDecimal.ZERO) == 0) && totalImpostoDevido != null) {
            log.info("⚠️ Usando 'Total do imposto devido' como fallback para 'Imposto devido': {}", totalImpostoDevido);
            impostoDevido = totalImpostoDevido;
        }

        log.info("💰 IMPOSTO DEVIDO - Base: {}, Devido: {}, Saldo a Pagar: {}",
                baseCalculoImposto, impostoDevido, saldoImpostoPagar);

        // Rendimentos e Deduções Gerais
        // Tentar primeiro o padrão específico para TOTAL na seção RENDIMENTOS
        // TRIBUTÁVEIS
        BigDecimal rendimentosTributaveis = extractValorMonetario(resumoPageText,
                RENDIMENTOS_TRIBUTAVEIS_TOTAL_PATTERN);
        if (rendimentosTributaveis == null) {
            // Fallback: padrão mais simples
            rendimentosTributaveis = extractValorMonetario(resumoPageText, RENDIMENTOS_TRIBUTAVEIS_PATTERN);
        }

        BigDecimal deducoes = extractValorMonetario(resumoPageText, DEDUCOES_TOTAL_PATTERN);
        BigDecimal impostoRetidoFonteTitular = extractValorMonetario(resumoPageText,
                IMPOSTO_RETIDO_FONTE_TITULAR_PATTERN);
        BigDecimal impostoPagoTotal = extractValorMonetario(resumoPageText, IMPOSTO_PAGO_TOTAL_PATTERN);
        BigDecimal impostoRestituir = extractValorMonetario(resumoPageText, IMPOSTO_RESTITUIR_PATTERN);

        log.info("📊 Rendimentos/Deduções - Rendimentos: {}, Deduções: {}, Restituir: {}",
                rendimentosTributaveis, deducoes, impostoRestituir);

        // DEDUÇÕES Individuais
        BigDecimal deducoesContribPrevOficial = extractValorMonetario(resumoPageText,
                DEDUCOES_CONTRIB_PREV_OFICIAL_PATTERN);
        BigDecimal deducoesContribPrevRRA = extractValorMonetario(resumoPageText, DEDUCOES_CONTRIB_PREV_RRA_PATTERN);
        BigDecimal deducoesContribPrevCompl = extractValorMonetario(resumoPageText,
                DEDUCOES_CONTRIB_PREV_COMPL_PATTERN);
        BigDecimal deducoesDependentes = extractValorMonetario(resumoPageText, DEDUCOES_DEPENDENTES_PATTERN);
        BigDecimal deducoesInstrucao = extractValorMonetario(resumoPageText, DEDUCOES_INSTRUCAO_PATTERN);
        BigDecimal deducoesMedicas = extractValorMonetario(resumoPageText, DEDUCOES_MEDICAS_PATTERN);
        BigDecimal deducoesPensaoJudicial = extractValorMonetario(resumoPageText, DEDUCOES_PENSAO_JUDICIAL_PATTERN);
        BigDecimal deducoesPensaoEscritura = extractValorMonetario(resumoPageText, DEDUCOES_PENSAO_ESCRITURA_PATTERN);
        BigDecimal deducoesPensaoRRA = extractValorMonetario(resumoPageText, DEDUCOES_PENSAO_RRA_PATTERN);
        BigDecimal deducoesLivroCaixa = extractValorMonetario(resumoPageText, DEDUCOES_LIVRO_CAIXA_PATTERN);

        log.info("📋 DEDUÇÕES - PrevOficial: {}, Médicas: {}, Instrução: {}",
                deducoesContribPrevOficial, deducoesMedicas, deducoesInstrucao);

        // IMPOSTO PAGO Individuais
        BigDecimal impostoRetidoFonteDependentes = extractValorMonetario(resumoPageText,
                IMPOSTO_RETIDO_FONTE_DEPENDENTES_PATTERN);
        BigDecimal carneLeaoTitular = extractValorMonetario(resumoPageText, CARNE_LEAO_TITULAR_PATTERN);
        BigDecimal carneLeaoDependentes = extractValorMonetario(resumoPageText, CARNE_LEAO_DEPENDENTES_PATTERN);
        BigDecimal impostoComplementar = extractValorMonetario(resumoPageText, IMPOSTO_COMPLEMENTAR_PATTERN);
        BigDecimal impostoPagoExterior = extractValorMonetario(resumoPageText, IMPOSTO_PAGO_EXTERIOR_PATTERN);
        BigDecimal impostoRetidoFonteLei11033 = extractValorMonetario(resumoPageText,
                IMPOSTO_RETIDO_FONTE_LEI_11033_PATTERN);
        BigDecimal impostoRetidoRRA = extractValorMonetario(resumoPageText, IMPOSTO_RETIDO_RRA_PATTERN);

        // Campos 2017+
        BigDecimal descontoSimplificado = extractValorMonetario(resumoPageText, DESCONTO_SIMPLIFICADO_PATTERN);
        BigDecimal aliquotaEfetiva = extractValorMonetario(resumoPageText, ALIQUOTA_EFETIVA_PATTERN);

        log.info("🔢 Campos 2017+ - Desconto Simplificado: {}, Alíquota Efetiva: {}",
                descontoSimplificado, aliquotaEfetiva);

        return new IncomeTaxDeclarationService.IncomeTaxInfo(
                nome, cpf, anoCalendario, exercicio,
                baseCalculoImposto, impostoDevido, deducaoIncentivo, impostoDevidoI,
                contribuicaoPrevEmpregadorDomestico, impostoDevidoII, impostoDevidoRRA,
                totalImpostoDevido, saldoImpostoPagar,
                rendimentosTributaveis, deducoes, impostoRetidoFonteTitular, impostoPagoTotal, impostoRestituir,
                deducoesContribPrevOficial, deducoesContribPrevRRA, deducoesContribPrevCompl,
                deducoesDependentes, deducoesInstrucao, deducoesMedicas,
                deducoesPensaoJudicial, deducoesPensaoEscritura, deducoesPensaoRRA, deducoesLivroCaixa,
                impostoRetidoFonteDependentes, carneLeaoTitular, carneLeaoDependentes,
                impostoComplementar, impostoPagoExterior, impostoRetidoFonteLei11033, impostoRetidoRRA,
                descontoSimplificado, aliquotaEfetiva);
    }

    /**
     * Extrai uma string usando um padrão regex.
     */
    private String extractString(String text, Pattern pattern) {
        if (text == null)
            return null;
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String result = matcher.group(1).trim();
            log.debug("✅ Extraído '{}' com padrão {}", result,
                    pattern.pattern().substring(0, Math.min(50, pattern.pattern().length())));
            return result;
        }
        return null;
    }

    /**
     * Extrai um valor monetário (BigDecimal) usando um padrão regex.
     * Converte formato brasileiro (1.234,56) para BigDecimal.
     */
    private BigDecimal extractValorMonetario(String text, Pattern pattern) {
        if (text == null)
            return null;

        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            // Procura o primeiro grupo que contém um valor
            String valorStr = null;
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null && matcher.group(i).matches("[\\d.,]+")) {
                    valorStr = matcher.group(i);
                    break;
                }
            }

            if (valorStr != null) {
                try {
                    // Converte formato brasileiro para padrão numérico
                    String valorNormalizado = valorStr.replace(".", "").replace(",", ".");
                    BigDecimal valor = new BigDecimal(valorNormalizado);
                    log.debug("💵 Valor extraído: {} -> {}", valorStr, valor);
                    return valor;
                } catch (NumberFormatException e) {
                    log.warn("⚠️ Erro ao converter valor: {}", valorStr);
                }
            }
        }
        return null;
    }
}
