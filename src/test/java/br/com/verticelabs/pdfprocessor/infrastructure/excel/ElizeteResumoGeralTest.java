package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.incometax.IrpfDeclaracaoDataMapper;
import br.com.verticelabs.pdfprocessor.application.selic.dto.SelicReceitaCalculoResponse;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrCalculoProgressivoService;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrDoacoesDeducaoCalculator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrSimuladorMotorService;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.infrastructure.config.IrTributacaoParametrosUtil;
import br.com.verticelabs.pdfprocessor.infrastructure.incometax.ITextIncomeTaxServiceImpl;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressão Resumo Geral — Elizete AC 2016 e 2018.
 */
class ElizeteResumoGeralTest {

    private static final Path ELIZETE_PDF_2016 = Paths.get(
            "temp/PASTADOCUMENTOSDESSIMULADORIRPF - Rogerio Rodrigues",
            "Elizete de Almeida Lins Santos - AEA AL",
            "2016",
            "DeclaraçãodeImpostodeRenda_2016.pdf");

    private static final Path ELIZETE_PDF_2018 = Paths.get(
            "temp/PASTADOCUMENTOSDESSIMULADORIRPF - Rogerio Rodrigues",
            "Elizete de Almeida Lins Santos - AEA AL",
            "2018",
            "DeclaraçãodeImpostodeRenda_2018.pdf");

    private static final BigDecimal PREV_PLANILHA_2018 = new BigDecimal("14884.95");

    private ExcelResumoGeralHelper resumoHelper;
    private IrParametrosAnuais params2016;
    private IrParametrosAnuais params2018;
    private List<IrTabelaTributacao> faixas2016;
    private List<IrTabelaTributacao> faixas2018;

    @BeforeEach
    void setUp() {
        IrSimuladorMotorService motor = new IrSimuladorMotorService(
                new IrCalculoProgressivoService(), new IrDoacoesDeducaoCalculator());
        ExcelIrpfSimulacaoMapper excelMapper = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator());
        resumoHelper = new ExcelResumoGeralHelper(motor, excelMapper);

        params2016 = IrParametrosAnuais.builder()
                .deducaoDependente(new BigDecimal("2275.08"))
                .limiteInstrucao(new BigDecimal("3561.50"))
                .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                .limiteInssDomestico(IrTributacaoParametrosUtil.limiteInssDomestico(2016))
                .build();
        params2018 = IrParametrosAnuais.builder()
                .deducaoDependente(new BigDecimal("2275.08"))
                .limiteInstrucao(new BigDecimal("3561.50"))
                .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                .limiteInssDomestico(IrTributacaoParametrosUtil.limiteInssDomestico(2018))
                .build();
        faixas2016 = criarFaixas(2016);
        faixas2018 = criarFaixas(2018);
    }

    @Test
    void extrairTipoResultadoPositivo_priorizaRestituirOuSaldo() {
        assertEquals(
                ExcelResumoGeralHelper.ORIGEM_IMPOSTO_A_RESTITUIR,
                resumoHelper.extrairTipoResultadoPositivo(new BigDecimal("100"), new BigDecimal("50")));
        assertEquals(
                ExcelResumoGeralHelper.ORIGEM_SALDO_IMPOSTO_A_PAGAR,
                resumoHelper.extrairTipoResultadoPositivo(BigDecimal.ZERO, new BigDecimal("50")));
        assertNull(resumoHelper.extrairTipoResultadoPositivo(BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void resumo2016_valoresBloco1e2Referencia() throws Exception {
        IrpfDeclaracaoData data = carregarElizete2016();
        Assumptions.assumeTrue("SIMPLIFICADO".equalsIgnoreCase(data.getTipoTributacao()));

        ExcelResumoGeralLinhaDTO linha = resumoHelper.montarLinha(
                "2016", data, BigDecimal.ZERO, faixas2016, params2016);

        // A declaração 2016 da Elizete tem SALDO A PAGAR 1.872,48 (imposto devido
        // 8.539,02 − pago 6.666,54), não restituição.
        assertEquals(new BigDecimal("1872.48"), linha.getValorDeclaracao());
        assertEquals(ExcelResumoGeralHelper.ORIGEM_SALDO_IMPOSTO_A_PAGAR, linha.getOrigemValorDeclaracao());
        // Sem impacto financeiro (imposto a pagar): a simulação completa não reduz o
        // saldo devido, então a coluna C repete o valor da declaração.
        assertEquals(new BigDecimal("1872.48"), linha.getValorSimulacao());
        assertEquals(ExcelResumoGeralHelper.ORIGEM_SALDO_IMPOSTO_A_PAGAR, linha.getOrigemValorSimulacao());
        assertEquals(BigDecimal.ZERO.setScale(2), linha.getPrincipal());
        assertEquals(ExcelResumoGeralHelper.OBS_SEM_IMPACTO, linha.getObservacao());
        assertEquals(LocalDate.of(2017, 4, 30), linha.getDataVencimento());
    }

    @Test
    void resumo2018_valoresBloco1e2Referencia() throws Exception {
        IrpfDeclaracaoData data = carregarElizete2018();

        ExcelResumoGeralLinhaDTO linha = resumoHelper.montarLinha(
                "2018", data, PREV_PLANILHA_2018, faixas2018, params2018);

        assertEquals(new BigDecimal("22884.35"), linha.getValorDeclaracao());
        assertEquals(ExcelResumoGeralHelper.ORIGEM_SALDO_IMPOSTO_A_PAGAR, linha.getOrigemValorDeclaracao());
        assertEquals(new BigDecimal("21905.40"), linha.getValorSimulacao());
        assertEquals(ExcelResumoGeralHelper.ORIGEM_SALDO_IMPOSTO_A_PAGAR, linha.getOrigemValorSimulacao());
        assertEquals(new BigDecimal("978.95"), linha.getPrincipal());
        assertEquals(ExcelResumoGeralHelper.OBS_IMPACTO, linha.getObservacao());
    }

    @Test
    void resumo2018_enriquecerComSelic_calculaCorrecao() {
        ExcelResumoGeralLinhaDTO base = ExcelResumoGeralLinhaDTO.builder()
                .anoCalendario("2018")
                .valorDeclaracao(new BigDecimal("22884.35"))
                .valorSimulacao(new BigDecimal("21905.40"))
                .principal(new BigDecimal("978.95"))
                .observacao(ExcelResumoGeralHelper.OBS_IMPACTO)
                .dataVencimento(LocalDate.of(2019, 4, 30))
                .build();

        BigDecimal taxa = new BigDecimal("65.78");
        BigDecimal corrigido = new BigDecimal("1622.90");
        SelicReceitaCalculoResponse selic = new SelicReceitaCalculoResponse(
                YearMonth.of(2019, 4),
                YearMonth.of(2025, 6),
                LocalDate.of(2019, 4, 30),
                LocalDate.of(2025, 6, 30),
                new BigDecimal("978.95"),
                taxa,
                new BigDecimal("1.6578"),
                corrigido,
                74,
                List.of());

        ExcelResumoGeralLinhaDTO enriquecida = base.enriquecerComSelic(selic);

        assertEquals(taxa, enriquecida.getSelicAcumulada());
        assertEquals(new BigDecimal("643.95"), enriquecida.getValorCorrecao());
        assertEquals(corrigido, enriquecida.getPrincipalMaisCorrecao());
    }

    @Test
    void calcularTotais_honorarios12Porcento() {
        ExcelResumoGeralLinhaDTO linha = ExcelResumoGeralLinhaDTO.builder()
                .principal(new BigDecimal("978.95"))
                .valorCorrecao(new BigDecimal("643.95"))
                .principalMaisCorrecao(new BigDecimal("1622.90"))
                .build();

        ExcelResumoGeralHelper.TotaisResumoGeral totais = resumoHelper.calcularTotais(List.of(linha));

        assertEquals(new BigDecimal("978.95"), totais.totalPrincipal());
        assertEquals(new BigDecimal("643.95"), totais.totalCorrecao());
        assertEquals(new BigDecimal("1622.90"), totais.totalPrincipalMaisCorrecao());
        assertEquals(new BigDecimal("194.75"), totais.honorarios());
        assertEquals(new BigDecimal("1428.15"), totais.valorReceber());
    }

    @Test
    void calcularTotais_honorariosPercentualCustomizado() {
        ExcelResumoGeralLinhaDTO linha = ExcelResumoGeralLinhaDTO.builder()
                .principalMaisCorrecao(new BigDecimal("1000.00"))
                .build();

        BigDecimal fracao15 = new BigDecimal("0.15");
        ExcelResumoGeralHelper.TotaisResumoGeral totais = resumoHelper.calcularTotais(List.of(linha), fracao15);

        assertEquals(new BigDecimal("150.00"), totais.honorarios());
        assertEquals(new BigDecimal("850.00"), totais.valorReceber());
    }

    @Test
    void ordenarPorAnoCalendario_reordenaListaForaDeOrdem() {
        List<ExcelResumoGeralLinhaDTO> foraDeOrdem = List.of(
                linhaAno("2023"),
                linhaAno("2016"),
                linhaAno("2024"),
                linhaAno("2018"),
                linhaAno("2017"));

        List<ExcelResumoGeralLinhaDTO> ordenadas = resumoHelper.ordenarPorAnoCalendario(foraDeOrdem);

        assertEquals(List.of("2016", "2017", "2018", "2023", "2024"),
                ordenadas.stream().map(ExcelResumoGeralLinhaDTO::getAnoCalendario).toList());
    }

    @Test
    void filtrarDeclaracoes_mantemApenasAnoComContracheque() {
        IrpfDeclaracaoData decl2015 = IrpfDeclaracaoData.builder()
                .anoCalendario("2015")
                .exercicio("2016")
                .build();
        IrpfDeclaracaoData decl2016 = IrpfDeclaracaoData.builder()
                .anoCalendario("2016")
                .exercicio("2017")
                .build();

        var todas = java.util.Map.of(
                "2015", decl2015,
                "2016", decl2016);

        var filtradas = resumoHelper.filtrarDeclaracoesPorAnosContracheque(todas, java.util.Set.of("2016"));

        assertEquals(1, filtradas.size());
        assertTrue(filtradas.containsKey("2016"));
        assertEquals("2017", filtradas.get("2016").getExercicio());
    }

    @Test
    void montarLinhas_ignoraDeclaracaoSemContrachequeDoAno() {
        IrpfDeclaracaoData decl2015 = IrpfDeclaracaoData.builder()
                .anoCalendario("2015")
                .exercicio("2016")
                .impostoRestituir(new BigDecimal("100.00"))
                .build();
        IrpfDeclaracaoData decl2016 = IrpfDeclaracaoData.builder()
                .anoCalendario("2016")
                .exercicio("2017")
                .impostoRestituir(new BigDecimal("200.00"))
                .build();

        var irpfDeclaracoes = java.util.Map.of("2015", decl2015, "2016", decl2016);
        var prevCompl = java.util.Map.of("2016", new BigDecimal("1000.00"));
        var tabelas = java.util.Map.of("2016", faixas2016);
        var parametros = java.util.Map.of("2016", params2016);

        var linhas = resumoHelper.montarLinhas(irpfDeclaracoes, prevCompl, tabelas, parametros);

        assertEquals(1, linhas.size());
        assertEquals("2016", linhas.get(0).getAnoCalendario());
    }

    @Test
    void montarLinhas_semParametrosDoAnoIr_naoFalha() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .anoCalendario("2016")
                .exercicio("2017")
                .impostoRestituir(new BigDecimal("500.00"))
                .build();

        var irpfDeclaracoes = java.util.Map.of("2016", data);
        var prevCompl = java.util.Map.of("2016", BigDecimal.ZERO);
        var parametros = java.util.Map.of("2016", params2016);

        var linhas = resumoHelper.montarLinhas(
                irpfDeclaracoes,
                prevCompl,
                java.util.Map.of("2016", faixas2016),
                parametros);

        assertEquals(1, linhas.size());
        assertEquals("2016", linhas.get(0).getAnoCalendario());
        assertNotNull(linhas.get(0).getValorSimulacao());
    }

    private ExcelResumoGeralLinhaDTO linhaAno(String ano) {
        return ExcelResumoGeralLinhaDTO.builder().anoCalendario(ano).build();
    }

    @Test
    void extrairResultadoPositivo_prefereRestituirQuandoAmbosPositivos() {
        assertEquals(new BigDecimal("100.00"),
                resumoHelper.extrairResultadoPositivo(new BigDecimal("100"), new BigDecimal("50")));
        assertEquals(new BigDecimal("200.00"),
                resumoHelper.extrairResultadoPositivo(BigDecimal.ZERO, new BigDecimal("200")));
    }

    @Test
    void extrairResultadoDeclaracao_prefereRestituirMesmoQuandoSaldoMaior() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .impostoRestituir(new BigDecimal("1872.48"))
                .saldoImpostoPagar(new BigDecimal("3605.63"))
                .build();
        assertEquals(new BigDecimal("1872.48"), resumoHelper.extrairResultadoPositivoDeclaracao(data));
    }

    @Test
    void calcularValorColunaC_saldoSimulado_retornaSaldo() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .saldoImpostoPagar(new BigDecimal("2575.94"))
                .build();
        var bloco2 = new ExcelResumoGeralHelper.ResultadoBloco2Simulacao(
                ZERO.setScale(2), new BigDecimal("36.06"), new BigDecimal("36.06"));

        assertEquals(new BigDecimal("36.06"), resumoHelper.calcularValorColunaC(data, bloco2));
    }

    @Test
    void calcularValorColunaC_restituirSimComSaldoDecl_retornaSoValorDaAba() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .saldoImpostoPagar(new BigDecimal("1503.80"))
                .impostoRestituir(BigDecimal.ZERO)
                .build();
        var bloco2 = new ExcelResumoGeralHelper.ResultadoBloco2Simulacao(
                new BigDecimal("4044.88"), ZERO.setScale(2), new BigDecimal("4044.88"));

        assertEquals(new BigDecimal("4044.88"), resumoHelper.calcularValorColunaC(data, bloco2));
    }

    @Test
    void calcularValorColunaC_restituirSimComRestituirDecl_naoSoma() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .impostoRestituir(new BigDecimal("1000.00"))
                .build();
        var bloco2 = new ExcelResumoGeralHelper.ResultadoBloco2Simulacao(
                new BigDecimal("1500.00"), ZERO.setScale(2), new BigDecimal("1500.00"));

        assertEquals(new BigDecimal("1500.00"), resumoHelper.calcularValorColunaC(data, bloco2));
    }

    @Test
    void calcularPrincipal_situacao1_saldoMenorNaSimulacao() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .saldoImpostoPagar(new BigDecimal("2575.94"))
                .build();
        var bloco2 = new ExcelResumoGeralHelper.ResultadoBloco2Simulacao(
                ZERO.setScale(2), new BigDecimal("36.06"), new BigDecimal("36.06"));
        BigDecimal b = new BigDecimal("2575.94");
        BigDecimal c = new BigDecimal("36.06");

        assertEquals(new BigDecimal("2539.88"),
                resumoHelper.calcularPrincipal(b, c, data, bloco2));
    }

    @Test
    void calcularPrincipal_situacao2_saldoMaiorNaSimulacao_semImpacto() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .saldoImpostoPagar(new BigDecimal("1000.00"))
                .build();
        var bloco2 = new ExcelResumoGeralHelper.ResultadoBloco2Simulacao(
                ZERO.setScale(2), new BigDecimal("1500.00"), new BigDecimal("1500.00"));

        assertEquals(BigDecimal.ZERO.setScale(2),
                resumoHelper.calcularPrincipal(new BigDecimal("1000.00"), new BigDecimal("1500.00"), data, bloco2));
    }

    @Test
    void calcularPrincipal_situacao3_restituirSimComSaldoDecl_somaBC() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .saldoImpostoPagar(new BigDecimal("1503.80"))
                .build();
        var bloco2 = new ExcelResumoGeralHelper.ResultadoBloco2Simulacao(
                new BigDecimal("4044.88"), ZERO.setScale(2), new BigDecimal("4044.88"));
        BigDecimal b = new BigDecimal("1503.80");
        BigDecimal c = new BigDecimal("4044.88");

        assertEquals(new BigDecimal("5548.68"), resumoHelper.calcularPrincipal(b, c, data, bloco2));
    }

    @Test
    void calcularPrincipal_situacao4_restituirSimComRestituirDecl_diferenca() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .impostoRestituir(new BigDecimal("1000.00"))
                .build();
        var bloco2 = new ExcelResumoGeralHelper.ResultadoBloco2Simulacao(
                new BigDecimal("1500.00"), ZERO.setScale(2), new BigDecimal("1500.00"));

        assertEquals(new BigDecimal("500.00"),
                resumoHelper.calcularPrincipal(new BigDecimal("1000.00"), new BigDecimal("1500.00"), data, bloco2));
    }

    /**
     * Célia AC 2017: declaração restitui 1.121,56; simulação completa passaria a PAGAR 194,77.
     * A simulação piora a situação → sem impacto financeiro (principal = 0).
     */
    @Test
    void calcularPrincipal_declRestituirSimSaldoPagar_semImpacto() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .impostoRestituir(new BigDecimal("1121.56"))
                .saldoImpostoPagar(ZERO)
                .build();
        var bloco2 = new ExcelResumoGeralHelper.ResultadoBloco2Simulacao(
                ZERO.setScale(2), new BigDecimal("194.77"), new BigDecimal("194.77"));

        assertEquals(BigDecimal.ZERO.setScale(2),
                resumoHelper.calcularPrincipal(
                        new BigDecimal("1121.56"), new BigDecimal("194.77"), data, bloco2));
    }

    /**
     * Declaração restitui 1.000; simulação restitui menos (900). Sem impacto: o
     * contribuinte não ganha nada com o aproveitamento (principal = 0).
     */
    @Test
    void calcularPrincipal_declRestituirSimRestituirMenor_semImpacto() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .impostoRestituir(new BigDecimal("1000.00"))
                .build();
        var bloco2 = new ExcelResumoGeralHelper.ResultadoBloco2Simulacao(
                new BigDecimal("900.00"), ZERO.setScale(2), new BigDecimal("900.00"));

        assertEquals(BigDecimal.ZERO.setScale(2),
                resumoHelper.calcularPrincipal(
                        new BigDecimal("1000.00"), new BigDecimal("900.00"), data, bloco2));
    }

    @Test
    void calcularValorColunaC_marcia2016_saldoSimulado() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .saldoImpostoPagar(new BigDecimal("356.98"))
                .build();
        var bloco2 = new ExcelResumoGeralHelper.ResultadoBloco2Simulacao(
                ZERO.setScale(2), new BigDecimal("89.87"), new BigDecimal("89.87"));

        assertEquals(new BigDecimal("89.87"), resumoHelper.calcularValorColunaC(data, bloco2));
        assertEquals(new BigDecimal("267.11"),
                resumoHelper.calcularPrincipal(
                        new BigDecimal("356.98"), new BigDecimal("89.87"), data, bloco2));
    }

    /**
     * Regra Célia: quando não há impacto financeiro (imposto a restituir), a coluna
     * "Valor Devido e ou a Restituir" deve repetir o valor da declaração, não o valor
     * simulado (menor). Cenário: restituição integral do imposto pago — a simulação
     * completa jamais supera isso, então principal = 0 (sem impacto).
     */
    @Test
    void montarLinha_semImpactoRestituir_repeteValorDaDeclaracao() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .anoCalendario("2016")
                .tipoTributacao("SIMPLIFICADO")
                .rendimentosTributaveisTotal(new BigDecimal("30000.00"))
                .descontoSimplificado(new BigDecimal("6000.00"))
                .impostoRetidoFonteTitular(new BigDecimal("50000.00"))
                .impostoPagoTotal(new BigDecimal("50000.00"))
                .impostoRestituir(new BigDecimal("50000.00"))
                .saldoImpostoPagar(ZERO)
                .build();

        ExcelResumoGeralLinhaDTO linha = resumoHelper.montarLinha(
                "2016", data, BigDecimal.ZERO, faixas2016, params2016);

        assertEquals(new BigDecimal("50000.00"), linha.getValorDeclaracao());
        assertEquals(ExcelResumoGeralHelper.ORIGEM_IMPOSTO_A_RESTITUIR, linha.getOrigemValorDeclaracao());
        assertEquals(BigDecimal.ZERO.setScale(2), linha.getPrincipal());
        assertEquals(ExcelResumoGeralHelper.OBS_SEM_IMPACTO, linha.getObservacao());
        // Coluna C repete a declaração (e não o valor simulado menor).
        assertEquals(linha.getValorDeclaracao(), linha.getValorSimulacao());
        assertEquals(ExcelResumoGeralHelper.ORIGEM_IMPOSTO_A_RESTITUIR, linha.getOrigemValorSimulacao());
    }

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private IrpfDeclaracaoData carregarElizete2016() throws Exception {
        Assumptions.assumeTrue(Files.exists(ELIZETE_PDF_2016),
                "PDF 2016 da Elizete não encontrado: " + ELIZETE_PDF_2016.toAbsolutePath());
        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        var info = service.extractIncomeTaxInfo(new FileInputStream(ELIZETE_PDF_2016.toFile())).block();
        assertNotNull(info);
        return new IrpfDeclaracaoDataMapper().fromIncomeTaxInfo(info);
    }

    private IrpfDeclaracaoData carregarElizete2018() throws Exception {
        Assumptions.assumeTrue(Files.exists(ELIZETE_PDF_2018),
                "PDF 2018 da Elizete não encontrado: " + ELIZETE_PDF_2018.toAbsolutePath());
        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        var info = service.extractIncomeTaxInfo(new FileInputStream(ELIZETE_PDF_2018.toFile())).block();
        assertNotNull(info);
        return new IrpfDeclaracaoDataMapper().fromIncomeTaxInfo(info);
    }

    private List<IrTabelaTributacao> criarFaixas(int ano) {
        List<IrTabelaTributacao> faixas = new ArrayList<>();
        faixas.add(faixa(ano, 1, "0", "22847.76", "0", "0"));
        faixas.add(faixa(ano, 2, "22847.77", "33919.80", "0.075", "1713.58"));
        faixas.add(faixa(ano, 3, "33919.81", "45012.60", "0.15", "4257.57"));
        faixas.add(faixa(ano, 4, "45012.61", "55976.16", "0.225", "7633.51"));
        faixas.add(faixa(ano, 5, "55976.17", null, "0.275", "10432.32"));
        return faixas;
    }

    private IrTabelaTributacao faixa(int ano, int n, String inf, String sup, String aliq, String ded) {
        return IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia("ANUAL")
                .faixa(n)
                .limiteInferior(new BigDecimal(inf))
                .limiteSuperior(sup != null ? new BigDecimal(sup) : null)
                .aliquota(new BigDecimal(aliq))
                .deducao(new BigDecimal(ded))
                .build();
    }
}
