package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.tributacao.IrCalculoProgressivoService;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrDoacoesDeducaoCalculator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrSimuladorMotorService;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.ModeloTributacaoResultDTO;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData.DoacaoEfetuadaIrpf;
import br.com.verticelabs.pdfprocessor.domain.tributacao.IrCodigoDeducao;
import br.com.verticelabs.pdfprocessor.infrastructure.config.IrTributacaoParametrosUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Nelson Cerqueira de Morais — ECA direto na declaração (3% do imposto) na Simulação 2.
 */
@DisplayName("Nelson 2017/2018 — incentivo na simulação")
class Nelson2017IncentivoSimulacaoTest {

    private static final BigDecimal PREV_PLANILHA_2017 = new BigDecimal("13555.07");

    private IrSimuladorMotorService motor;
    private ExcelIrpfSimulacaoMapper mapper;
    private ExcelResumoGeralHelper resumoHelper;
    private IrParametrosAnuais params;
    private List<IrTabelaTributacao> faixas;

    @BeforeEach
    void setUp() {
        motor = new IrSimuladorMotorService(new IrCalculoProgressivoService(), new IrDoacoesDeducaoCalculator());
        mapper = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator());
        resumoHelper = new ExcelResumoGeralHelper(motor, mapper);
        params = IrParametrosAnuais.builder()
                .deducaoDependente(new BigDecimal("2275.08"))
                .limiteInstrucao(new BigDecimal("3561.50"))
                .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                .limiteInssDomestico(IrTributacaoParametrosUtil.limiteInssDomestico(2017))
                .build();
        faixas = List.of(
                faixa(1, "0", "22847.76", "0", "0"),
                faixa(2, "22847.77", "33919.80", "0.075", "1713.58"),
                faixa(3, "33919.81", "45012.60", "0.15", "4257.57"),
                faixa(4, "45012.61", "55976.16", "0.225", "7633.51"),
                faixa(5, "55976.17", null, "0.275", "10432.32"));
    }

    @Test
    @DisplayName("2017: imposto 125.298,96 − ECA direto 3.758,97 (3%) = I 121.539,99; saldo 25.724,24")
    void nelson2017_ecaDiretoLimitadoA3PctDoImpostoSimulado() {
        IrpfDeclaracaoData data = nelson2017();

        SimuladorIrpfRequest request = mapper.fromDeclaracao(data, PREV_PLANILHA_2017, true);
        request.setInssDomesticoComoCreditoImposto(true);
        assertEquals(0, new BigDecimal("3822.40").compareTo(request.getIncentivoDiretoEca()));
        assertEquals(0, BigDecimal.ZERO.compareTo(request.getDeducaoIncentivo()));

        ModeloTributacaoResultDTO modelo = motor.simular(request, faixas, params).getModeloCompleto();
        assertEquals(new BigDecimal("493568.30"), modelo.getBaseCalculo());
        assertEquals(new BigDecimal("125298.96"), modelo.getImpostoDevidoAposReducao());
        assertEquals(new BigDecimal("3758.97"), modelo.getDeducoesEspeciais());
        assertEquals(new BigDecimal("121539.99"), modelo.getImpostoDevidoFinal());
        assertNull(modelo.getImpostoDevidoII());
        assertEquals(new BigDecimal("121539.99"), modelo.getResumo().getTotalImpostoDevido());

        ExcelResumoGeralHelper.ResultadoBloco2Simulacao bloco2 = resumoHelper.calcularResultadoBloco2Simulacao(
                data, PREV_PLANILHA_2017, faixas, params);
        assertEquals(new BigDecimal("25724.24"), bloco2.saldoPagar());
        assertEquals(new BigDecimal("0.00"), bloco2.restituir());
    }

    @Test
    @DisplayName("2018: doações só cód. 99 — incentivo zero e I = imposto devido")
    void nelson2018_semIncentivo() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .anoCalendario("2018")
                .exercicio("2019")
                .tipoTributacao("COMPLETO")
                .rendimentosTributaveisTotal(new BigDecimal("176877.48"))
                .impostoRetidoFonteTitular(new BigDecimal("26613.12"))
                .impostoPagoTotal(new BigDecimal("26613.12"))
                .deducaoIncentivo(BigDecimal.ZERO)
                .doacoesEfetuadas(List.of(
                        doacao("99", "CASA DE IDOSOS", "1300.00"),
                        doacao("99", "OUTRA ENTIDADE", "240.00")))
                .build();

        SimuladorIrpfRequest request = mapper.fromDeclaracao(data, BigDecimal.ZERO, true);
        request.setInssDomesticoComoCreditoImposto(true);
        ModeloTributacaoResultDTO modelo = motor.simular(request, faixas, params).getModeloCompleto();

        assertEquals(new BigDecimal("0.00"), modelo.getDeducoesEspeciais());
        assertEquals(modelo.getImpostoDevidoAposReducao(), modelo.getImpostoDevidoFinal());
    }

    @Test
    @DisplayName("Classificação: 'Municipal - ...' cód. 40/43 é direta; entidade comum cód. 40 e cód. 99 não")
    void classificaDoacaoDiretaNaDeclaracao() {
        assertEquals(IrCodigoDeducao.C_40, ExcelIrpfSimulacaoMapper.codigoDiretaNaDeclaracao(
                doacao("40", "Municipal - MG - SAO DOMINGOS DO PRATA", "3822.40")));
        assertEquals(IrCodigoDeducao.C_43, ExcelIrpfSimulacaoMapper.codigoDiretaNaDeclaracao(
                doacao("43", "Estadual - SP - FUNDO ESTADUAL DO IDOSO", "100.00")));
        assertNull(ExcelIrpfSimulacaoMapper.codigoDiretaNaDeclaracao(
                doacao("40", "FUNDO MUNICIPAL DOS DIREITOS DA CRIANCA", "100.00")));
        assertNull(ExcelIrpfSimulacaoMapper.codigoDiretaNaDeclaracao(
                doacao("99", "Municipal - MG - QUALQUER", "100.00")));
    }

    @Test
    @DisplayName("ECA cód. 40 durante o ano continua no incentivo comum (só teto 6%)")
    void ecaDuranteAno_naoVaiParaDireta() {
        IrpfDeclaracaoData data = nelson2017().toBuilder()
                .deducaoIncentivo(new BigDecimal("500.00"))
                .doacoesEfetuadas(List.of(doacao("40", "FUNDO MUNICIPAL DOS DIREITOS DA CRIANCA", "500.00")))
                .build();

        SimuladorIrpfRequest request = mapper.fromDeclaracao(data, PREV_PLANILHA_2017, true);

        assertEquals(0, new BigDecimal("500.00").compareTo(request.getDeducaoIncentivo()));
        assertEquals(0, BigDecimal.ZERO.compareTo(request.getIncentivoDiretoEca()));
    }

    private IrpfDeclaracaoData nelson2017() {
        return IrpfDeclaracaoData.builder()
                .anoCalendario("2017")
                .exercicio("2018")
                .tipoTributacao("COMPLETO")
                .rendimentosTributaveisTotal(new BigDecimal("570107.95"))
                .rendimentosTributaveisTitularPJ(new BigDecimal("570107.95"))
                .contribuicaoPrevidenciaSocial(new BigDecimal("1825.32"))
                .contribuicaoPrevidenciaPrivada(new BigDecimal("5866.04"))
                .despesasInstrucao(new BigDecimal("3561.50"))
                .despesasMedicas(new BigDecimal("10123.30"))
                .pensaoAlimenticiaEscrituraPublica(new BigDecimal("47474.46"))
                .impostoDevido(new BigDecimal("127413.44"))
                .deducaoIncentivo(new BigDecimal("3822.40"))
                .impostoDevidoI(new BigDecimal("123591.04"))
                .totalImpostoDevido(new BigDecimal("123591.04"))
                .impostoRetidoFonteTitular(new BigDecimal("95815.75"))
                .impostoPagoTotal(new BigDecimal("95815.75"))
                .saldoImpostoPagar(new BigDecimal("27775.29"))
                .doacoesEfetuadas(List.of(
                        doacao("99", "ENTIDADE NAO DEDUTIVEL", "270.00"),
                        doacao("40", "Municipal - MG - SAO DOMINGOS DO PRATA", "3822.40")))
                .build();
    }

    private static DoacaoEfetuadaIrpf doacao(String codigo, String nome, String valor) {
        return DoacaoEfetuadaIrpf.builder()
                .codigo(codigo)
                .nomeBeneficiario(nome)
                .valorDoado(new BigDecimal(valor))
                .build();
    }

    private static IrTabelaTributacao faixa(int n, String inf, String sup, String aliq, String ded) {
        return IrTabelaTributacao.builder()
                .anoCalendario(2017)
                .tipoIncidencia("ANUAL")
                .faixa(n)
                .limiteInferior(new BigDecimal(inf))
                .limiteSuperior(sup != null ? new BigDecimal(sup) : null)
                .aliquota(new BigDecimal(aliq))
                .deducao(new BigDecimal(ded))
                .build();
    }
}
