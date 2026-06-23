package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.tributacao.IrSimuladorMotorService;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.ModeloTributacaoResultDTO;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfResponse;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Regras de negócio da aba Resumo Geral (colunas B–H por ano-calendário).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExcelResumoGeralHelper {

    private static final RoundingMode RM = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final Comparator<String> COMPARADOR_ANO_CALENDARIO = Comparator.comparingInt(
            ExcelResumoGeralHelper::parseAnoCalendario);

    public static final BigDecimal PERCENTUAL_HONORARIOS_DEFAULT = new BigDecimal("0.12");
    /** @deprecated use {@link #PERCENTUAL_HONORARIOS_DEFAULT} */
    @Deprecated
    public static final BigDecimal PERCENTUAL_HONORARIOS = PERCENTUAL_HONORARIOS_DEFAULT;
    public static final String OBS_SEM_IMPACTO = "Sem impacto financeiro - Sistema de tributação";
    public static final String OBS_IMPACTO = "Impacto financeiro";
    public static final String RODAPE_LABEL = "Responsável:";
    public static final String RODAPE_NOME = "Vanderson Luiz Burracini";
    public static final String RODAPE_ECONOMISTA = "Economista - CORECON SP 2ª Região Reg. 36.432";

    public static final Map<String, LocalDate> DATAS_VENCIMENTO;

    static {
        Map<String, LocalDate> map = new LinkedHashMap<>();
        map.put("2016", LocalDate.of(2017, 4, 30));
        map.put("2017", LocalDate.of(2018, 4, 30));
        map.put("2018", LocalDate.of(2019, 4, 30));
        map.put("2019", LocalDate.of(2020, 6, 30));
        map.put("2020", LocalDate.of(2021, 5, 31));
        map.put("2021", LocalDate.of(2022, 5, 31));
        map.put("2022", LocalDate.of(2023, 5, 31));
        map.put("2023", LocalDate.of(2024, 5, 31));
        map.put("2024", LocalDate.of(2025, 5, 30));
        map.put("2025", LocalDate.of(2026, 5, 29));
        DATAS_VENCIMENTO = Collections.unmodifiableMap(map);
    }

    private final IrSimuladorMotorService simuladorMotorService;
    private final ExcelIrpfSimulacaoMapper excelIrpfSimulacaoMapper;

    /**
     * Resultado do bloco 2 (simulação planilha): restituição, saldo e valor positivo para Resumo Geral.
     */
    public record ResultadoBloco2Simulacao(
            BigDecimal restituir,
            BigDecimal saldoPagar,
            BigDecimal valorPositivo) {
    }

    public List<ExcelResumoGeralLinhaDTO> montarLinhas(
            Map<String, IrpfDeclaracaoData> irpfDeclaracoes,
            Map<String, BigDecimal> prevComplPorAno,
            Map<String, List<IrTabelaTributacao>> tabelasTributacao,
            Map<String, IrParametrosAnuais> parametrosTributacao) {

        if (irpfDeclaracoes == null || irpfDeclaracoes.isEmpty()) {
            return List.of();
        }

        Map<String, IrpfDeclaracaoData> declaracoesAlinhadas = prevComplPorAno != null && !prevComplPorAno.isEmpty()
                ? filtrarDeclaracoesPorAnosContracheque(irpfDeclaracoes, prevComplPorAno.keySet())
                : irpfDeclaracoes;

        if (declaracoesAlinhadas.isEmpty()) {
            return List.of();
        }

        List<ExcelResumoGeralLinhaDTO> linhas = new ArrayList<>();
        TreeMap<String, IrpfDeclaracaoData> ordenados = new TreeMap<>(COMPARADOR_ANO_CALENDARIO);
        ordenados.putAll(declaracoesAlinhadas);

        for (Map.Entry<String, IrpfDeclaracaoData> entry : ordenados.entrySet()) {
            String ano = entry.getKey();
            IrpfDeclaracaoData data = entry.getValue();
            if (data == null) {
                continue;
            }
            BigDecimal prevCompl = prevComplPorAno != null
                    ? prevComplPorAno.getOrDefault(ano, ZERO)
                    : ZERO;
            List<IrTabelaTributacao> faixas = tabelasTributacao != null
                    ? tabelasTributacao.getOrDefault(ano, List.of())
                    : List.of();
            IrParametrosAnuais params = parametrosTributacao != null ? parametrosTributacao.get(ano) : null;
            linhas.add(montarLinha(ano, data, prevCompl, faixas, params));
        }
        return linhas;
    }

    /**
     * Mantém apenas declarações cujo ano-calendário possui contracheque processado.
     * Ex.: contracheque 2016 → usa declaração exercício 2017 / ano-calendário 2016;
     * declaração exercício 2016 / ano-calendário 2015 é ignorada.
     */
    public Map<String, IrpfDeclaracaoData> filtrarDeclaracoesPorAnosContracheque(
            Map<String, IrpfDeclaracaoData> declaracoes,
            java.util.Collection<String> anosContracheque) {

        if (declaracoes == null || declaracoes.isEmpty()) {
            return Map.of();
        }
        if (anosContracheque == null || anosContracheque.isEmpty()) {
            return Map.of();
        }

        java.util.Set<String> anos = anosContracheque.stream()
                .filter(a -> a != null && !a.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toSet());

        Map<String, IrpfDeclaracaoData> filtradas = new LinkedHashMap<>();
        for (Map.Entry<String, IrpfDeclaracaoData> entry : declaracoes.entrySet()) {
            String ano = entry.getKey() != null ? entry.getKey().trim() : null;
            if (ano != null && anos.contains(ano)) {
                filtradas.put(ano, entry.getValue());
            } else if (ano != null) {
                IrpfDeclaracaoData data = entry.getValue();
                log.info(
                        "Declaração IR ignorada: ano-calendário {} (exercício {}) — sem contracheque do ano",
                        ano,
                        data != null ? data.getExercicio() : "?");
            }
        }
        return filtradas;
    }

    public List<ExcelResumoGeralLinhaDTO> ordenarPorAnoCalendario(List<ExcelResumoGeralLinhaDTO> linhas) {
        if (linhas == null || linhas.isEmpty()) {
            return List.of();
        }
        return linhas.stream()
                .sorted(Comparator.comparingInt(l -> parseAnoCalendario(l.getAnoCalendario())))
                .toList();
    }

    private static int parseAnoCalendario(String ano) {
        if (ano == null || ano.isBlank()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(ano.trim());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    public ExcelResumoGeralLinhaDTO montarLinha(
            String ano,
            IrpfDeclaracaoData data,
            BigDecimal prevComplPlanilha,
            List<IrTabelaTributacao> faixasTabela,
            IrParametrosAnuais paramsAno) {

        BigDecimal valorDeclaracao = extrairResultadoPositivoDeclaracao(data);
        BigDecimal valorSimulacao = calcularResultadoSimulacaoPlanilhaSeguro(
                ano, data, prevComplPlanilha, faixasTabela, paramsAno, valorDeclaracao);
        valorSimulacao = ajustarValorSimulacaoQuandoSaldoDeclaracaoMaior(
                valorDeclaracao, valorSimulacao, data);
        BigDecimal principal = calcularPrincipal(valorDeclaracao, valorSimulacao);
        String observacao = principal.compareTo(ZERO) > 0 ? OBS_IMPACTO : OBS_SEM_IMPACTO;

        return ExcelResumoGeralLinhaDTO.builder()
                .anoCalendario(ano)
                .valorDeclaracao(valorDeclaracao)
                .valorSimulacao(valorSimulacao)
                .principal(principal)
                .selicAcumulada(ZERO)
                .valorCorrecao(ZERO)
                .principalMaisCorrecao(ZERO)
                .observacao(observacao)
                .dataVencimento(DATAS_VENCIMENTO.get(ano))
                .build();
    }

    public BigDecimal extrairResultadoPositivoDeclaracao(IrpfDeclaracaoData data) {
        return extrairResultadoPositivo(data.getImpostoRestituir(), data.getSaldoImpostoPagar());
    }

    /**
     * Quando a simulação (col. C) supera a declaração (col. B) mas a declaração entregue
     * tinha saldo a pagar maior que restituição, usa o valor da col. B na col. C.
     */
    public BigDecimal ajustarValorSimulacaoQuandoSaldoDeclaracaoMaior(
            BigDecimal valorDeclaracao,
            BigDecimal valorSimulacao,
            IrpfDeclaracaoData data) {
        if (nvl(valorSimulacao).compareTo(nvl(valorDeclaracao)) <= 0) {
            return valorSimulacao;
        }
        BigDecimal saldoDeclaracao = nvl(data.getSaldoImpostoPagar());
        BigDecimal restituirDeclaracao = nvl(data.getImpostoRestituir());
        if (saldoDeclaracao.compareTo(restituirDeclaracao) > 0) {
            return nvl(valorDeclaracao).setScale(2, RM);
        }
        return valorSimulacao;
    }

    public BigDecimal extrairResultadoPositivo(BigDecimal restituir, BigDecimal saldoPagar) {
        BigDecimal r = nvl(restituir);
        BigDecimal s = nvl(saldoPagar);
        if (r.compareTo(ZERO) > 0) {
            return r.setScale(2, RM);
        }
        if (s.compareTo(ZERO) > 0) {
            return s.setScale(2, RM);
        }
        return ZERO.setScale(2, RM);
    }

    public BigDecimal calcularPrincipal(BigDecimal valorDeclaracao, BigDecimal valorSimulacao) {
        return nvl(valorDeclaracao).subtract(nvl(valorSimulacao)).max(ZERO).setScale(2, RM);
    }

    public ResultadoBloco2Simulacao calcularResultadoBloco2Simulacao(
            IrpfDeclaracaoData data,
            BigDecimal prevComplPlanilha,
            List<IrTabelaTributacao> faixasTabela,
            IrParametrosAnuais paramsAno) {

        SimuladorIrpfRequest request = excelIrpfSimulacaoMapper.fromDeclaracao(
                data, prevComplPlanilha, true);
        request.setInssDomesticoComoCreditoImposto(true);

        SimuladorIrpfResponse response = simuladorMotorService.simular(request, faixasTabela, paramsAno);
        ModeloTributacaoResultDTO modelo = response.getModeloCompleto();

        BigDecimal impostoProgressivoTotal = modelo.getImpostoDevidoII() != null
                ? modelo.getImpostoDevidoII()
                : modelo.getImpostoDevidoFinal();
        BigDecimal totalDevido = modelo.getResumo() != null && modelo.getResumo().getTotalImpostoDevido() != null
                ? modelo.getResumo().getTotalImpostoDevido()
                : nvl(impostoProgressivoTotal).add(nvl(data.getImpostoSobreRRA()));
        BigDecimal totalPago = calcularTotalImpostoPagoDeclaracao(data);

        return calcularResultadoPositivoDeTotais(totalPago, totalDevido);
    }

    public BigDecimal calcularResultadoSimulacaoPlanilha(
            IrpfDeclaracaoData data,
            BigDecimal prevComplPlanilha,
            List<IrTabelaTributacao> faixasTabela,
            IrParametrosAnuais paramsAno) {
        return calcularResultadoBloco2Simulacao(data, prevComplPlanilha, faixasTabela, paramsAno).valorPositivo();
    }

    private BigDecimal calcularResultadoSimulacaoPlanilhaSeguro(
            String ano,
            IrpfDeclaracaoData data,
            BigDecimal prevComplPlanilha,
            List<IrTabelaTributacao> faixasTabela,
            IrParametrosAnuais paramsAno,
            BigDecimal valorDeclaracaoFallback) {
        if (paramsAno == null || faixasTabela == null || faixasTabela.isEmpty()) {
            log.warn(
                    "Resumo Geral ano {}: parâmetros ou tabela de tributação ausentes — usando valor da declaração",
                    ano);
            return valorDeclaracaoFallback;
        }
        return calcularResultadoSimulacaoPlanilha(data, prevComplPlanilha, faixasTabela, paramsAno);
    }

    public ResultadoBloco2Simulacao calcularResultadoPositivoDeTotais(
            BigDecimal totalPago, BigDecimal totalDevido) {
        BigDecimal restituir = nvl(totalPago).subtract(nvl(totalDevido));
        BigDecimal saldoPagar = nvl(totalDevido).subtract(nvl(totalPago));
        if (restituir.compareTo(ZERO) > 0) {
            return new ResultadoBloco2Simulacao(
                    restituir.setScale(2, RM),
                    ZERO.setScale(2, RM),
                    restituir.setScale(2, RM));
        }
        BigDecimal saldoPositivo = saldoPagar.max(ZERO).setScale(2, RM);
        return new ResultadoBloco2Simulacao(
                ZERO.setScale(2, RM),
                saldoPositivo,
                saldoPositivo);
    }

    public BigDecimal calcularTotalImpostoPagoDeclaracao(IrpfDeclaracaoData data) {
        if (data.getImpostoPagoTotal() != null) {
            return data.getImpostoPagoTotal();
        }
        return nvl(data.getImpostoRetidoFonteTitular())
                .add(nvl(data.getImpostoRetidoDependentes()))
                .add(nvl(data.getCarneLeaoTitular()))
                .add(nvl(data.getCarneLeaoDependentes()))
                .add(nvl(data.getImpostoComplementar()))
                .add(nvl(data.getImpostoPagoExterior()))
                .add(nvl(data.getImpostoRetidoFonteLei11033()))
                .add(nvl(data.getImpostoRetidoRRA()));
    }

    public record TotaisResumoGeral(
            BigDecimal totalPrincipal,
            BigDecimal totalCorrecao,
            BigDecimal totalPrincipalMaisCorrecao,
            BigDecimal honorarios,
            BigDecimal valorReceber) {
    }

    public TotaisResumoGeral calcularTotais(List<ExcelResumoGeralLinhaDTO> linhas) {
        return calcularTotais(linhas, PERCENTUAL_HONORARIOS_DEFAULT);
    }

    public TotaisResumoGeral calcularTotais(List<ExcelResumoGeralLinhaDTO> linhas, BigDecimal percentualFracao) {
        BigDecimal fracao = percentualFracao != null ? percentualFracao : PERCENTUAL_HONORARIOS_DEFAULT;
        BigDecimal totalD = ZERO;
        BigDecimal totalF = ZERO;
        BigDecimal totalG = ZERO;
        for (ExcelResumoGeralLinhaDTO linha : linhas) {
            totalD = totalD.add(nvl(linha.getPrincipal()));
            totalF = totalF.add(nvl(linha.getValorCorrecao()));
            totalG = totalG.add(nvl(linha.getPrincipalMaisCorrecao()));
        }
        totalD = totalD.setScale(2, RM);
        totalF = totalF.setScale(2, RM);
        totalG = totalG.setScale(2, RM);
        BigDecimal honorarios = totalG.multiply(fracao).setScale(2, RM);
        BigDecimal valorReceber = totalG.subtract(honorarios).setScale(2, RM);
        return new TotaisResumoGeral(totalD, totalF, totalG, honorarios, valorReceber);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : ZERO;
    }
}
