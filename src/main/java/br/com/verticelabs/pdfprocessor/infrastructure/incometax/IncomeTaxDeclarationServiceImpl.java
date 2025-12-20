package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService;
import br.com.verticelabs.pdfprocessor.domain.service.PdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncomeTaxDeclarationServiceImpl implements IncomeTaxDeclarationService {

        private final PdfService pdfService;

        // Padrão para encontrar "Ano-Calendário" seguido de um ano
        // Usa . para aceitar qualquer encoding de caracteres acentuados
        private static final Pattern ANO_CALENDARIO_PATTERN = Pattern.compile(
                        "(?i)ano.calend.rio\\s*(\\d{4})",
                        Pattern.CASE_INSENSITIVE);

        // Padrão para encontrar "EXERCÍCIO" seguido de um ano
        // Usa . para aceitar qualquer encoding de caracteres acentuados
        private static final Pattern EXERCICIO_PATTERN = Pattern.compile(
                        "(?i)exerc.cio\\s*(\\d{4})",
                        Pattern.CASE_INSENSITIVE);

        // Padrão para encontrar "NOME:" seguido do nome completo
        private static final Pattern NOME_PATTERN = Pattern.compile(
                        "(?i)nome\\s*:?\\s*([A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ][A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ\\s]{2,}?)(?=\\s*(?:CPF|$|\\n))",
                        Pattern.MULTILINE);

        // Padrão para encontrar CPF no formato XXX.XXX.XXX-XX
        private static final Pattern CPF_PATTERN = Pattern.compile(
                        "CPF\\s*:?\\s*(\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2})",
                        Pattern.CASE_INSENSITIVE);

        // Padrões para encontrar valores da seção IMPOSTO DEVIDO
        private static final Pattern BASE_CALCULO_IMPOSTO_PATTERN = Pattern.compile(
                        "(?i)base\\s+de\\s+c[aá]lculo\\s+do\\s+imposto[\\s:]*[R$\\s]*([\\d]{1,3}(?:[.\\s]?[\\d]{3})*(?:,\\d{2})?)",
                        Pattern.CASE_INSENSITIVE);

        // Padrão para "Imposto devido" (sem I, II ou RRA)
        // Usa negative lookahead para excluir variantes I, II, RRA
        // PDF é extraído em colunas: label em uma linha, valor na próxima
        private static final Pattern IMPOSTO_DEVIDO_PATTERN = Pattern.compile(
                        "(?i)imposto\\s+devido(?!\\s+(?:I|II|RRA))[\\s\\r\\n]+([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

        private static final Pattern DEDUCAO_INCENTIVO_PATTERN = Pattern.compile(
                        "(?i)dedu[çc][ãa]o\\s+de\\s+incentivo[\\s:]*[R$\\s]*([\\d]{1,3}(?:[.\\s]?[\\d]{3})*(?:,\\d{2})?)",
                        Pattern.CASE_INSENSITIVE);

        private static final Pattern IMPOSTO_DEVIDO_I_PATTERN = Pattern.compile(
                        "(?i)imposto\\s+devido\\s+I[\\s:]*[R$\\s]*([\\d]{1,3}(?:[.\\s]?[\\d]{3})*(?:,\\d{2})?)",
                        Pattern.CASE_INSENSITIVE);

        private static final Pattern CONTRIBUICAO_PREV_EMPREGADOR_DOMESTICO_PATTERN = Pattern.compile(
                        "(?i)contribui[çc][ãa]o\\s+prev\\.?\\s+empregador\\s+dom[eé]stico[\\s:]*[R$\\s]*([\\d]{1,3}(?:[.\\s]?[\\d]{3})*(?:,\\d{2})?)",
                        Pattern.CASE_INSENSITIVE);

        private static final Pattern IMPOSTO_DEVIDO_II_PATTERN = Pattern.compile(
                        "(?i)imposto\\s+devido\\s+II[\\s:]*[R$\\s]*([\\d]{1,3}(?:[.\\s]?[\\d]{3})*(?:,\\d{2})?)",
                        Pattern.CASE_INSENSITIVE);

        private static final Pattern IMPOSTO_DEVIDO_RRA_PATTERN = Pattern.compile(
                        "(?i)imposto\\s+devido\\s+RRA[\\s:]*[R$\\s]*([\\d]{1,3}(?:[.\\s]?[\\d]{3})*(?:,\\d{2})?)",
                        Pattern.CASE_INSENSITIVE);

        private static final Pattern TOTAL_IMPOSTO_DEVIDO_PATTERN = Pattern.compile(
                        "(?i)total\\s+do\\s+imposto\\s+devido[\\s:]*[R$\\s]*([\\d]{1,3}(?:[.\\s]?[\\d]{3})*(?:,\\d{2})?)",
                        Pattern.CASE_INSENSITIVE);

        // Padrão para "Saldo de imposto a pagar" - aceita quebras de linha entre label
        // e valor
        // (PDF pode ter duas colunas: label na primeira, valor na segunda)
        private static final Pattern SALDO_IMPOSTO_PAGAR_PATTERN = Pattern.compile(
                        "(?i)saldo\\s+de\\s+imposto\\s+a\\s+pagar[\\s\\r\\n:]*[R$\\s]*([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

        // ==========================================
        // NOVOS PADRÕES SOLICITADOS
        // ==========================================

        // Rendimentos Tributáveis - TOTAL
        // Usa . para aceitar qualquer encoding de caracteres acentuados
        // Busca "RENDIMENTOS TRIBUTÁVEIS" ... "TOTAL" ... Valor
        private static final Pattern RENDIMENTOS_TRIBUTAVEIS_TOTAL_PATTERN = Pattern.compile(
                        "(?i)rendimentos\\s+tribut.veis.*?total\\s+([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        // Deduções - TOTAL
        // Usa . para aceitar qualquer encoding de caracteres acentuados
        private static final Pattern DEDUCOES_TOTAL_PATTERN = Pattern.compile(
                        "(?i)dedu..es.*?total\\s+([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        // ==========================================
        // PADRÕES INDIVIDUAIS DE DEDUÇÕES
        // ==========================================

        // Contribuição à previdência oficial e complementar pública (até limite
        // patrocinador)
        private static final Pattern DEDUCOES_CONTRIB_PREV_OFICIAL_PATTERN = Pattern.compile(
                        "(?i)contribui..o\\s+.\\s+previd.ncia\\s+oficial\\s+e\\s+.\\s+previd.ncia\\s+complementar\\s+p.blica.*?([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        // Contribuição à previdência oficial (RRA)
        private static final Pattern DEDUCOES_CONTRIB_PREV_RRA_PATTERN = Pattern.compile(
                        "(?i)contribui..o\\s+.\\s+previd.ncia\\s+oficial\\s+\\(Rendimentos\\s+recebidos\\s+acumuladamente\\)\\s*([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE);

        // Contribuição à previdência complementar, privada, e Fapi
        private static final Pattern DEDUCOES_CONTRIB_PREV_COMPL_PATTERN = Pattern.compile(
                        "(?i)contribui..o\\s+.\\s+previd.ncia\\s+complementar.*?privada.*?Fapi\\s*([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        // Dependentes
        private static final Pattern DEDUCOES_DEPENDENTES_PATTERN = Pattern.compile(
                        "(?i)Dependentes\\s+([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE);

        // Despesas com instrução
        private static final Pattern DEDUCOES_INSTRUCAO_PATTERN = Pattern.compile(
                        "(?i)Despesas\\s+com\\s+instru..o\\s+([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE);

        // Despesas médicas
        private static final Pattern DEDUCOES_MEDICAS_PATTERN = Pattern.compile(
                        "(?i)Despesas\\s+m.dicas\\s+([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE);

        // Pensão alimentícia judicial
        private static final Pattern DEDUCOES_PENSAO_JUDICIAL_PATTERN = Pattern.compile(
                        "(?i)Pens.o\\s+aliment.cia\\s+judicial\\s+([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE);

        // Pensão alimentícia por escritura pública
        private static final Pattern DEDUCOES_PENSAO_ESCRITURA_PATTERN = Pattern.compile(
                        "(?i)Pens.o\\s+aliment.cia\\s+por\\s+escritura\\s+p.blica\\s+([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE);

        // Pensão alimentícia judicial (RRA)
        private static final Pattern DEDUCOES_PENSAO_RRA_PATTERN = Pattern.compile(
                        "(?i)Pens.o\\s+aliment.cia\\s+judicial\\s+\\(Rendimentos\\s+recebidos\\s+acumuladamente\\)\\s*([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE);

        // Livro caixa
        private static final Pattern DEDUCOES_LIVRO_CAIXA_PATTERN = Pattern.compile(
                        "(?i)Livro\\s+caixa\\s+([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE);

        // Imposto pago - Imposto retido na fonte do titular
        private static final Pattern IMPOSTO_RETIDO_FONTE_TITULAR_PATTERN = Pattern.compile(
                        "(?i)imposto\\s+retido\\s+na\\s+fonte\\s+do\\s+titular[\\s\\r\\n]*([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

        // Imposto pago - Total do imposto pago
        private static final Pattern IMPOSTO_PAGO_TOTAL_PATTERN = Pattern.compile(
                        "(?i)total\\s+do\\s+imposto\\s+pago[\\s\\r\\n]*([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

        // Imposto a restituir
        private static final Pattern IMPOSTO_RESTITUIR_PATTERN = Pattern.compile(
                        "(?i)imposto\\s+a\\s+restituir[\\s\\r\\n]*([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

        // ==========================================
        // PADRÕES INDIVIDUAIS DE IMPOSTO PAGO
        // ==========================================

        // Imp. retido na fonte dos dependentes
        private static final Pattern IMPOSTO_RETIDO_FONTE_DEPENDENTES_PATTERN = Pattern.compile(
                        "(?i)imp\\.?\\s+retido\\s+na\\s+fonte\\s+dos\\s+dependentes\\s+([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE);

        // Carnê-Leão do titular
        private static final Pattern CARNE_LEAO_TITULAR_PATTERN = Pattern.compile(
                        "(?i)carn.-le.o\\s+do\\s+titular\\s+([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE);

        // Carnê-Leão dos dependentes
        private static final Pattern CARNE_LEAO_DEPENDENTES_PATTERN = Pattern.compile(
                        "(?i)carn.-le.o\\s+dos\\s+dependentes\\s+([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE);

        // Imposto complementar
        private static final Pattern IMPOSTO_COMPLEMENTAR_PATTERN = Pattern.compile(
                        "(?i)imposto\\s+complementar\\s+([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE);

        // Imposto pago no exterior
        private static final Pattern IMPOSTO_PAGO_EXTERIOR_PATTERN = Pattern.compile(
                        "(?i)imposto\\s+pago\\s+no\\s+exterior\\s+([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE);

        // Imposto retido na fonte (Lei nº 11.033/2004)
        private static final Pattern IMPOSTO_RETIDO_FONTE_LEI_11033_PATTERN = Pattern.compile(
                        "(?i)imposto\\s+retido\\s+na\\s+fonte\\s+\\(Lei.*?11\\.?033.*?\\)\\s*([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE);

        // Imposto retido RRA
        private static final Pattern IMPOSTO_RETIDO_RRA_PATTERN = Pattern.compile(
                        "(?i)imposto\\s+retido\\s+RRA\\s+([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                        Pattern.CASE_INSENSITIVE);

        @Override
        public Mono<IncomeTaxInfo> extractIncomeTaxInfo(InputStream inputStream) {
                log.info("Iniciando extração de informações da declaração de IR");

                // Ler todos os bytes do inputStream primeiro (pois vamos precisar ler múltiplas
                // vezes)
                return Mono.fromCallable(() -> {
                        byte[] bytes = inputStream.readAllBytes();
                        inputStream.close();
                        return bytes;
                })
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(bytes -> {
                                        // Primeiro, encontrar a página RESUMO e extrair informações da primeira página
                                        return pdfService.getTotalPages(new java.io.ByteArrayInputStream(bytes))
                                                        .flatMap(totalPages -> {
                                                                log.info("PDF tem {} páginas. Extraindo informações...",
                                                                                totalPages);

                                                                // Extrair texto da primeira página (onde geralmente
                                                                // estão nome, CPF e
                                                                // exercício)
                                                                Mono<String> primeiraPaginaText = pdfService
                                                                                .extractTextFromPage(
                                                                                                new java.io.ByteArrayInputStream(
                                                                                                                bytes),
                                                                                                1);

                                                                // Encontrar página RESUMO
                                                                Mono<Integer> resumoPageNumber = findResumoPage(bytes,
                                                                                totalPages);

                                                                return Mono.zip(primeiraPaginaText, resumoPageNumber);
                                                        })
                                                        .flatMap(tuple -> {
                                                                String primeiraPaginaText = tuple.getT1();
                                                                Integer resumoPageNumber = tuple.getT2();

                                                                log.info("Página RESUMO encontrada: página {}",
                                                                                resumoPageNumber);

                                                                // Extrair texto da página RESUMO
                                                                return pdfService.extractTextFromPage(
                                                                                new java.io.ByteArrayInputStream(bytes),
                                                                                resumoPageNumber)
                                                                                .map(resumoPageText -> {
                                                                                        // DEBUG: Ver TODO o texto da
                                                                                        // página RESUMO para entender o
                                                                                        // layout
                                                                                        if (resumoPageText != null) {
                                                                                                log.info("🔍 DEBUG - Página RESUMO COMPLETA (raw): [{}]",
                                                                                                                resumoPageText.replace(
                                                                                                                                "\n",
                                                                                                                                "\\n")
                                                                                                                                .replace("\r", "\\r"));
                                                                                        }

                                                                                        // Extrair informações
                                                                                        String nome = extractNome(
                                                                                                        primeiraPaginaText); // Nome
                                                                                                                             // geralmente
                                                                                                                             // está
                                                                                                                             // na
                                                                                                                             // primeira
                                                                                                                             // página
                                                                                                                             // ou
                                                                                                                             // cabeçalhos
                                                                                        if (nome == null)
                                                                                                nome = extractNome(
                                                                                                                resumoPageText);

                                                                                        String cpf = extractCpf(
                                                                                                        primeiraPaginaText);
                                                                                        if (cpf == null)
                                                                                                cpf = extractCpf(
                                                                                                                resumoPageText);

                                                                                        // Tenta extrair Exercício e
                                                                                        // Ano-Calendário do RESUMO
                                                                                        // primeiro (onde o usuário
                                                                                        // indicou), depois da 1ª pág
                                                                                        String exercicio = extractExercicio(
                                                                                                        resumoPageText);
                                                                                        if (exercicio == null) {
                                                                                                exercicio = extractExercicio(
                                                                                                                primeiraPaginaText);
                                                                                        }

                                                                                        String anoCalendario = extractAnoCalendario(
                                                                                                        resumoPageText);
                                                                                        if (anoCalendario == null) {
                                                                                                anoCalendario = extractAnoCalendario(
                                                                                                                primeiraPaginaText);
                                                                                        }

                                                                                        // Extrair todos os valores da
                                                                                        // seção IMPOSTO DEVIDO
                                                                                        BigDecimal baseCalculoImposto = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        BASE_CALCULO_IMPOSTO_PATTERN);

                                                                                        // DEBUG: Mostrar parte
                                                                                        // relevante do texto para
                                                                                        // diagnóstico
                                                                                        int idxImposto = resumoPageText
                                                                                                        .toUpperCase()
                                                                                                        .indexOf("IMPOSTO DEVIDO");
                                                                                        if (idxImposto >= 0) {
                                                                                                int endIdx = Math.min(
                                                                                                                idxImposto + 300,
                                                                                                                resumoPageText.length());
                                                                                                log.info("🔍 DEBUG - Texto 'IMPOSTO DEVIDO': [{}]",
                                                                                                                resumoPageText.substring(
                                                                                                                                idxImposto,
                                                                                                                                endIdx)
                                                                                                                                .replace("\n", "\\n")
                                                                                                                                .replace("\r", "\\r"));
                                                                                        }

                                                                                        // Extrair "Imposto devido" com
                                                                                        // estratégia alternativa se
                                                                                        // necessário
                                                                                        BigDecimal impostoDevido = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        IMPOSTO_DEVIDO_PATTERN);
                                                                                        if (impostoDevido == null
                                                                                                        || impostoDevido.compareTo(
                                                                                                                        BigDecimal.ZERO) == 0) {
                                                                                                // Tentar estratégia
                                                                                                // alternativa: buscar
                                                                                                // todas as ocorrências
                                                                                                // e pegar a
                                                                                                // primeira que não seja
                                                                                                // I, II ou RRA
                                                                                                impostoDevido = extractImpostoDevidoAlternativo(
                                                                                                                resumoPageText);
                                                                                        }

                                                                                        BigDecimal deducaoIncentivo = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        DEDUCAO_INCENTIVO_PATTERN);
                                                                                        BigDecimal impostoDevidoI = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        IMPOSTO_DEVIDO_I_PATTERN);
                                                                                        BigDecimal contribuicaoPrevEmpregadorDomestico = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        CONTRIBUICAO_PREV_EMPREGADOR_DOMESTICO_PATTERN);
                                                                                        BigDecimal impostoDevidoII = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        IMPOSTO_DEVIDO_II_PATTERN);
                                                                                        BigDecimal impostoDevidoRRA = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        IMPOSTO_DEVIDO_RRA_PATTERN);
                                                                                        BigDecimal totalImpostoDevido = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        TOTAL_IMPOSTO_DEVIDO_PATTERN);
                                                                                        BigDecimal saldoImpostoPagar = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        SALDO_IMPOSTO_PAGAR_PATTERN);

                                                                                        // Estratégia alternativa para
                                                                                        // "Saldo de imposto a pagar" se
                                                                                        // o padrão
                                                                                        // principal falhar
                                                                                        // (PDF pode ter duas colunas:
                                                                                        // label na primeira, valor na
                                                                                        // segunda)
                                                                                        if (saldoImpostoPagar == null) {
                                                                                                saldoImpostoPagar = extractSaldoImpostoPagarAlternativo(
                                                                                                                resumoPageText);
                                                                                        }

                                                                                        // FALLBACK: Se "Imposto devido"
                                                                                        // não foi extraído corretamente
                                                                                        // (devido ao PDF ter duas
                                                                                        // colunas misturadas na
                                                                                        // extração),
                                                                                        // usar "Total do imposto
                                                                                        // devido" que tem o mesmo valor
                                                                                        if ((impostoDevido == null
                                                                                                        || impostoDevido.compareTo(
                                                                                                                        BigDecimal.ZERO) == 0)
                                                                                                        && totalImpostoDevido != null
                                                                                                        && totalImpostoDevido
                                                                                                                        .compareTo(BigDecimal.ZERO) > 0) {
                                                                                                log.info(
                                                                                                                "⚠️ Usando 'Total do imposto devido' como fallback para 'Imposto devido': {}",
                                                                                                                totalImpostoDevido);
                                                                                                impostoDevido = totalImpostoDevido;
                                                                                        }

                                                                                        // Novos campos
                                                                                        BigDecimal rendimentosTributaveis = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        RENDIMENTOS_TRIBUTAVEIS_TOTAL_PATTERN);
                                                                                        BigDecimal deducoes = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        DEDUCOES_TOTAL_PATTERN);
                                                                                        BigDecimal impostoRetidoFonteTitular = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        IMPOSTO_RETIDO_FONTE_TITULAR_PATTERN);
                                                                                        BigDecimal impostoPagoTotal = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        IMPOSTO_PAGO_TOTAL_PATTERN);
                                                                                        BigDecimal impostoRestituir = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        IMPOSTO_RESTITUIR_PATTERN);

                                                                                        // Campos individuais de
                                                                                        // DEDUÇÕES
                                                                                        BigDecimal deducoesContribPrevOficial = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        DEDUCOES_CONTRIB_PREV_OFICIAL_PATTERN);
                                                                                        BigDecimal deducoesContribPrevRRA = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        DEDUCOES_CONTRIB_PREV_RRA_PATTERN);
                                                                                        BigDecimal deducoesContribPrevCompl = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        DEDUCOES_CONTRIB_PREV_COMPL_PATTERN);
                                                                                        BigDecimal deducoesDependentes = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        DEDUCOES_DEPENDENTES_PATTERN);
                                                                                        BigDecimal deducoesInstrucao = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        DEDUCOES_INSTRUCAO_PATTERN);
                                                                                        BigDecimal deducoesMedicas = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        DEDUCOES_MEDICAS_PATTERN);
                                                                                        BigDecimal deducoesPensaoJudicial = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        DEDUCOES_PENSAO_JUDICIAL_PATTERN);
                                                                                        BigDecimal deducoesPensaoEscritura = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        DEDUCOES_PENSAO_ESCRITURA_PATTERN);
                                                                                        BigDecimal deducoesPensaoRRA = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        DEDUCOES_PENSAO_RRA_PATTERN);
                                                                                        BigDecimal deducoesLivroCaixa = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        DEDUCOES_LIVRO_CAIXA_PATTERN);

                                                                                        // Campos individuais de IMPOSTO
                                                                                        // PAGO
                                                                                        BigDecimal impostoRetidoFonteDependentes = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        IMPOSTO_RETIDO_FONTE_DEPENDENTES_PATTERN);
                                                                                        BigDecimal carneLeaoTitular = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        CARNE_LEAO_TITULAR_PATTERN);
                                                                                        BigDecimal carneLeaoDependentes = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        CARNE_LEAO_DEPENDENTES_PATTERN);
                                                                                        BigDecimal impostoComplementar = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        IMPOSTO_COMPLEMENTAR_PATTERN);
                                                                                        BigDecimal impostoPagoExterior = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        IMPOSTO_PAGO_EXTERIOR_PATTERN);
                                                                                        BigDecimal impostoRetidoFonteLei11033 = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        IMPOSTO_RETIDO_FONTE_LEI_11033_PATTERN);
                                                                                        BigDecimal impostoRetidoRRA = extractValorMonetario(
                                                                                                        resumoPageText,
                                                                                                        IMPOSTO_RETIDO_RRA_PATTERN);

                                                                                        log.info(
                                                                                                        "Informações extraídas - Nome: {}, CPF: {}, Exercício: {}, Ano-Calendário: {}",
                                                                                                        nome, cpf,
                                                                                                        exercicio,
                                                                                                        anoCalendario);
                                                                                        log.info(
                                                                                                        "Valores IMPOSTO DEVIDO - Base: {}, Devido: {}, Dedução: {}, Devido I: {}, Contribuição: {}, Devido II: {}, RRA: {}, Total: {}, Saldo a Pagar: {}",
                                                                                                        baseCalculoImposto,
                                                                                                        impostoDevido,
                                                                                                        deducaoIncentivo,
                                                                                                        impostoDevidoI,
                                                                                                        contribuicaoPrevEmpregadorDomestico,
                                                                                                        impostoDevidoII,
                                                                                                        impostoDevidoRRA,
                                                                                                        totalImpostoDevido,
                                                                                                        saldoImpostoPagar);

                                                                                        log.info(
                                                                                                        "Novos Campos - Rendimentos: {}, Deduções: {}, I.Retido Titular: {}, I. Pago Total: {}, A Restituir: {}",
                                                                                                        rendimentosTributaveis,
                                                                                                        deducoes,
                                                                                                        impostoRetidoFonteTitular,
                                                                                                        impostoPagoTotal,
                                                                                                        impostoRestituir);

                                                                                        log.info(
                                                                                                        "Campos DEDUÇÕES - ContribPrev: {}, ContribPrevRRA: {}, ContribPrevCompl: {}, Depend: {}, Instrução: {}, Médicas: {}, PensãoJud: {}, PensãoEsc: {}, PensãoRRA: {}, LivroCaixa: {}",
                                                                                                        deducoesContribPrevOficial,
                                                                                                        deducoesContribPrevRRA,
                                                                                                        deducoesContribPrevCompl,
                                                                                                        deducoesDependentes,
                                                                                                        deducoesInstrucao,
                                                                                                        deducoesMedicas,
                                                                                                        deducoesPensaoJudicial,
                                                                                                        deducoesPensaoEscritura,
                                                                                                        deducoesPensaoRRA,
                                                                                                        deducoesLivroCaixa);

                                                                                        log.info(
                                                                                                        "Campos IMPOSTO PAGO - RetidoDep: {}, CarneLeaoTit: {}, CarneLeaoDep: {}, Compl: {}, Exterior: {}, Lei11033: {}, RRA: {}",
                                                                                                        impostoRetidoFonteDependentes,
                                                                                                        carneLeaoTitular,
                                                                                                        carneLeaoDependentes,
                                                                                                        impostoComplementar,
                                                                                                        impostoPagoExterior,
                                                                                                        impostoRetidoFonteLei11033,
                                                                                                        impostoRetidoRRA);

                                                                                        return new IncomeTaxInfo(nome,
                                                                                                        cpf,
                                                                                                        anoCalendario,
                                                                                                        exercicio,
                                                                                                        baseCalculoImposto,
                                                                                                        impostoDevido,
                                                                                                        deducaoIncentivo,
                                                                                                        impostoDevidoI,
                                                                                                        contribuicaoPrevEmpregadorDomestico,
                                                                                                        impostoDevidoII,
                                                                                                        impostoDevidoRRA,
                                                                                                        totalImpostoDevido,
                                                                                                        saldoImpostoPagar,
                                                                                                        rendimentosTributaveis,
                                                                                                        deducoes,
                                                                                                        impostoRetidoFonteTitular,
                                                                                                        impostoPagoTotal,
                                                                                                        impostoRestituir,
                                                                                                        // Campos
                                                                                                        // DEDUÇÕES
                                                                                                        deducoesContribPrevOficial,
                                                                                                        deducoesContribPrevRRA,
                                                                                                        deducoesContribPrevCompl,
                                                                                                        deducoesDependentes,
                                                                                                        deducoesInstrucao,
                                                                                                        deducoesMedicas,
                                                                                                        deducoesPensaoJudicial,
                                                                                                        deducoesPensaoEscritura,
                                                                                                        deducoesPensaoRRA,
                                                                                                        deducoesLivroCaixa,
                                                                                                        // Campos
                                                                                                        // IMPOSTO PAGO
                                                                                                        impostoRetidoFonteDependentes,
                                                                                                        carneLeaoTitular,
                                                                                                        carneLeaoDependentes,
                                                                                                        impostoComplementar,
                                                                                                        impostoPagoExterior,
                                                                                                        impostoRetidoFonteLei11033,
                                                                                                        impostoRetidoRRA);
                                                                                });
                                                        })
                                                        .onErrorResume(e -> {
                                                                log.error("Erro ao extrair informações da declaração de IR",
                                                                                e);
                                                                return Mono.error(e);
                                                        });
                                });
        }

        /**
         * Encontra o número da página que contém "RESUMO".
         */
        private Mono<Integer> findResumoPage(byte[] pdfBytes, int totalPages) {
                // Processar páginas sequencialmente até encontrar "RESUMO"
                return Flux.range(1, totalPages)
                                .concatMap(pageNumber -> {
                                        return pdfService.extractTextFromPage(
                                                        new java.io.ByteArrayInputStream(pdfBytes), pageNumber)
                                                        .flatMap(pageText -> {
                                                                if (pageText != null && pageText.toUpperCase()
                                                                                .contains("RESUMO")) {
                                                                        log.debug("Página {} contém 'RESUMO'",
                                                                                        pageNumber);
                                                                        return Mono.just(pageNumber);
                                                                }
                                                                return Mono.empty();
                                                        });
                                })
                                .next()
                                .switchIfEmpty(Mono.error(
                                                new IllegalArgumentException("Página RESUMO não encontrada no PDF")));
        }

        /**
         * Extrai o Ano-Calendário do texto.
         */
        private String extractAnoCalendario(String text) {
                log.info("🔍 Tentando extrair Ano-Calendário do texto (primeiros 200 chars): [{}]",
                                text != null ? text.substring(0, Math.min(text.length(), 200)).replace("\n", "\\n")
                                                .replace("\r", "\\r") : "NULL");
                Matcher matcher = ANO_CALENDARIO_PATTERN.matcher(text);
                if (matcher.find()) {
                        String ano = matcher.group(1);
                        log.info("✅ Ano-Calendário extraído: {}", ano);
                        return ano;
                }
                log.warn("❌ Ano-Calendário NÃO encontrado no texto");
                return null;
        }

        /**
         * Extrai um valor monetário do texto usando um padrão específico.
         */
        private BigDecimal extractValorMonetario(String text, Pattern pattern) {
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                        String valorStr = matcher.group(1);
                        log.debug("Valor encontrado (string): {} (padrão: {})", valorStr, pattern.pattern());

                        // Converter para Double, tratando formato brasileiro (ponto como separador de
                        // milhar, vírgula como decimal)
                        try {
                                // Remove pontos (separadores de milhar) and replace comma with dot
                                String valorNormalizado = valorStr.replace(".", "").replace(",", ".");
                                BigDecimal valor = new BigDecimal(valorNormalizado);
                                log.debug("Valor convertido: {}", valor);
                                return valor;
                        } catch (NumberFormatException e) {
                                log.warn("Erro ao converter valor: {}", valorStr, e);
                                return null;
                        }
                } else {
                        log.debug("Nenhum valor encontrado para o padrão: {}", pattern.pattern());
                }
                return null;
        }

        /**
         * Extrai "Imposto devido" usando estratégia alternativa: busca todas as
         * ocorrências e pega a primeira que não seja I, II ou RRA.
         */
        private BigDecimal extractImpostoDevidoAlternativo(String text) {
                // Padrão para encontrar todas as ocorrências de "Imposto devido" seguidas de um
                // valor (PDF é extraído em colunas, então valor pode estar na próxima linha)
                Pattern pattern = Pattern.compile(
                                "(?i)imposto\\s+devido(?:\\s+(I|II|RRA))?[\\s\\r\\n:]*[R$\\s]*([\\d]{1,3}(?:[.\\s]?[\\d]{3})*(?:,\\d{2})?)",
                                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

                Matcher matcher = pattern.matcher(text);
                while (matcher.find()) {
                        String variante = matcher.group(1); // I, II, RRA ou null
                        String valorStr = matcher.group(2);

                        // Se não for I, II ou RRA, este é o "Imposto devido" que queremos
                        if (variante == null || variante.trim().isEmpty()) {
                                log.info("Imposto devido encontrado (alternativo): {}", valorStr);
                                try {
                                        String valorNormalizado = valorStr.replace(".", "").replace(",", ".");
                                        BigDecimal valor = new BigDecimal(valorNormalizado);
                                        log.info("Valor convertido (alternativo): {}", valor);
                                        return valor;
                                } catch (NumberFormatException e) {
                                        log.warn("Erro ao converter valor (alternativo): {}", valorStr, e);
                                }
                        }
                }

                log.warn("Nenhum 'Imposto devido' encontrado com estratégia alternativa");
                return null;
        }

        /**
         * Extrai "Saldo de imposto a pagar" usando estratégia alternativa:
         * Busca o texto "SALDO DE IMPOSTO A PAGAR" e procura pelo valor nas linhas
         * próximas.
         * Útil quando o PDF tem duas colunas e o valor está na segunda coluna.
         * 
         * Estratégia: Busca o label e depois procura pelo PRIMEIRO valor monetário
         * próximo,
         * que é o mais provável de estar diretamente relacionado ao label.
         */
        private BigDecimal extractSaldoImpostoPagarAlternativo(String text) {
                // Buscar a posição do texto "SALDO DE IMPOSTO A PAGAR"
                Pattern labelPattern = Pattern.compile(
                                "(?i)saldo\\s+de\\s+imposto\\s+a\\s+pagar",
                                Pattern.CASE_INSENSITIVE);

                Matcher labelMatcher = labelPattern.matcher(text);
                if (labelMatcher.find()) {
                        int labelEnd = labelMatcher.end();

                        // Extrair uma janela de texto após o label (até 200 caracteres para pegar
                        // apenas valores próximos)
                        int windowStart = labelEnd;
                        int windowEnd = Math.min(windowStart + 200, text.length());
                        String window = text.substring(windowStart, windowEnd);

                        log.debug("🔍 DEBUG - Janela após 'SALDO DE IMPOSTO A PAGAR': [{}]",
                                        window.replace("\n", "\\n").replace("\r", "\\r"));

                        // Procurar pelo PRIMEIRO valor monetário na janela (formato brasileiro:
                        // X.XXX,XX)
                        Pattern valuePattern = Pattern.compile(
                                        "([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                                        Pattern.CASE_INSENSITIVE);

                        Matcher valueMatcher = valuePattern.matcher(window);

                        // Retornar o PRIMEIRO valor encontrado (mais próximo do label)
                        if (valueMatcher.find()) {
                                String valorStr = valueMatcher.group(1);
                                try {
                                        String valorNormalizado = valorStr.replace(".", "").replace(",", ".");
                                        BigDecimal valor = new BigDecimal(valorNormalizado);

                                        log.info("Saldo de imposto a pagar encontrado (alternativo): {} (valor: {})",
                                                        valorStr, valor);
                                        return valor;
                                } catch (NumberFormatException e) {
                                        log.warn("Erro ao converter valor na janela: {}", valorStr, e);
                                }
                        } else {
                                log.warn("Nenhum valor monetário encontrado na janela após 'SALDO DE IMPOSTO A PAGAR'");
                        }
                } else {
                        log.warn("Texto 'SALDO DE IMPOSTO A PAGAR' não encontrado para estratégia alternativa");
                }

                return null;
        }

        /**
         * Extrai o nome da pessoa do texto.
         */
        private String extractNome(String text) {
                Matcher matcher = NOME_PATTERN.matcher(text);
                if (matcher.find()) {
                        String nome = matcher.group(1).trim();
                        log.debug("Nome extraído: {}", nome);
                        return nome;
                }
                return null;
        }

        /**
         * Extrai o CPF do texto.
         */
        private String extractCpf(String text) {
                Matcher matcher = CPF_PATTERN.matcher(text);
                if (matcher.find()) {
                        String cpf = matcher.group(1);
                        log.debug("CPF extraído: {}", cpf);
                        return cpf;
                }
                return null;
        }

        /**
         * Extrai o exercício do texto.
         */
        private String extractExercicio(String text) {
                Matcher matcher = EXERCICIO_PATTERN.matcher(text);
                if (matcher.find()) {
                        String exercicio = matcher.group(1);
                        log.debug("Exercício extraído: {}", exercicio);
                        return exercicio;
                }
                return null;
        }
}
