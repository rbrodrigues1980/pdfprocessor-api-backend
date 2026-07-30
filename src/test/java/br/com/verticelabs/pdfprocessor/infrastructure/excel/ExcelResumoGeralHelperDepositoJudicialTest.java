package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.tributacao.IrCalculoProgressivoService;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrDoacoesDeducaoCalculator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrSimuladorMotorService;
import br.com.verticelabs.pdfprocessor.domain.model.InformeRendimentosData;
import br.com.verticelabs.pdfprocessor.domain.model.InformeRendimentosData.InformacaoComplementarJudiciaria;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.infrastructure.config.IrTributacaoParametrosUtil;
import br.com.verticelabs.pdfprocessor.infrastructure.pdf.InformeRendimentosFuncefExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Depósito judicial do Informe na simulação / Resumo Geral")
class ExcelResumoGeralHelperDepositoJudicialTest {

    private static final String FIXTURE_TEXT = """
            1. FONTE PAGADORA PESSOA JURÍDICA OU PESSOA FÍSICA
            CNPJ
            Telefone
            00.436.923/0001-90
            0800 706 9000
            Razão Social
            FUNDAÇÃO DOS ECONOMIÁRIOS FEDERAIS
            Endereço
            SCN Q. 02 BLOCO A.
            2. PESSOA FÍSICA BENEFICIÁRIO DOS RENDIMENTOS
            Ano Calendário
            CPF
            NOME COMPLETO
            2018
            162.092.735-72
            PAULO AUGUSTO LOPES DA CRUZ
            NATUREZA DO RENDIMENTO
            Benefícios / Resgates de Entidade de Previdência Privada
            3. RENDIMENTOS TRIBUTÁVEIS, DEDUÇÕES E IMPOSTO
            FUNCEF
            7. INFORMAÇÕES COMPLEMENTARES
            Proc.Jud.
            1007809-57.2017.4.01.3300
             -
            03/06/2018
             -
            10
             - CIVEL BA  - Contr. Extr.:
            12.618,07
              - IRRF :
            3.470,00
              - Contr. Extr.
            13
            º :
            1.904,02
            - IRRF
            13
            º :
            523,61
            8. RESPONSÁVEL PELAS INFORMAÇÕES
            COMPROVANTE DE RENDIMENTOS PAGOS E DE RETENÇÃO DE IMPOSTO DE RENDA NA
            FONTE
            """;

    private ExcelResumoGeralHelper helper;
    private InformeRendimentosData informePaulo2018;
    private IrParametrosAnuais params2018;
    private List<IrTabelaTributacao> faixas2018;

    @BeforeEach
    void setUp() {
        IrSimuladorMotorService motor = new IrSimuladorMotorService(
                new IrCalculoProgressivoService(), new IrDoacoesDeducaoCalculator());
        helper = new ExcelResumoGeralHelper(motor, new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator()));
        informePaulo2018 = new InformeRendimentosFuncefExtractor().extract(FIXTURE_TEXT);
        params2018 = IrParametrosAnuais.builder()
                .deducaoDependente(new BigDecimal("2275.08"))
                .limiteInstrucao(new BigDecimal("3561.50"))
                .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                .limiteInssDomestico(IrTributacaoParametrosUtil.limiteInssDomestico(2018))
                .build();
        faixas2018 = List.of(
                IrTabelaTributacao.builder()
                        .anoCalendario(2018)
                        .tipoIncidencia("ANUAL")
                        .faixa(1)
                        .limiteInferior(BigDecimal.ZERO)
                        .limiteSuperior(new BigDecimal("22847.76"))
                        .aliquota(BigDecimal.ZERO)
                        .deducao(BigDecimal.ZERO)
                        .build(),
                IrTabelaTributacao.builder()
                        .anoCalendario(2018)
                        .tipoIncidencia("ANUAL")
                        .faixa(2)
                        .limiteInferior(new BigDecimal("22847.77"))
                        .limiteSuperior(new BigDecimal("33919.80"))
                        .aliquota(new BigDecimal("0.075"))
                        .deducao(new BigDecimal("1713.58"))
                        .build(),
                IrTabelaTributacao.builder()
                        .anoCalendario(2018)
                        .tipoIncidencia("ANUAL")
                        .faixa(3)
                        .limiteInferior(new BigDecimal("33919.81"))
                        .limiteSuperior(new BigDecimal("45012.60"))
                        .aliquota(new BigDecimal("0.15"))
                        .deducao(new BigDecimal("4257.57"))
                        .build(),
                IrTabelaTributacao.builder()
                        .anoCalendario(2018)
                        .tipoIncidencia("ANUAL")
                        .faixa(4)
                        .limiteInferior(new BigDecimal("45012.61"))
                        .limiteSuperior(new BigDecimal("55976.16"))
                        .aliquota(new BigDecimal("0.225"))
                        .deducao(new BigDecimal("7633.51"))
                        .build(),
                IrTabelaTributacao.builder()
                        .anoCalendario(2018)
                        .tipoIncidencia("ANUAL")
                        .faixa(5)
                        .limiteInferior(new BigDecimal("55976.17"))
                        .limiteSuperior(null)
                        .aliquota(new BigDecimal("0.275"))
                        .deducao(new BigDecimal("10432.32"))
                        .build());
    }

    @Test
    @DisplayName("Soma IRRF + IRRF 13º do fixture Paulo 2018 = 3993,61")
    void somarIrrf_paulo2018() {
        assertEquals(new BigDecimal("3993.61"), helper.somarIrrfDepositoJudicial(informePaulo2018));
    }

    @Test
    @DisplayName("Label de depósito judicial no formato do Excel de referência")
    void formatLabel_paulo2018() {
        InformacaoComplementarJudiciaria info = informePaulo2018.getInformacoesComplementares().get(0);
        String label = helper.formatLabelDepositoJudicial(info);
        assertEquals(
                "Imposto Pago - Através de Depósito Judicial - Processo Jud 1007809-57.2017.4.01.3300"
                        + " - 03/06/2018 - 10 - " + info.getVaraOuLocal().trim(),
                label);
        assertTrue(label.contains("CIVEL"));
    }

    @Test
    @DisplayName("Total pago da simulação = DIRPF + 3470 + 523,61")
    void totalPagoSimulacao_incluiDepositos() {
        IrpfDeclaracaoData dirpf = IrpfDeclaracaoData.builder()
                .anoCalendario("2018")
                .exercicio("2019")
                .tipoTributacao("COMPLETO")
                .rendimentosTributaveisTotal(new BigDecimal("100000.00"))
                .impostoPagoTotal(new BigDecimal("9974.48"))
                .build();

        BigDecimal totalDirpf = helper.calcularTotalImpostoPagoDeclaracao(dirpf);
        BigDecimal judicial = helper.somarIrrfDepositoJudicial(informePaulo2018);
        BigDecimal totalPago = totalDirpf.add(judicial);

        assertEquals(new BigDecimal("9974.48"), totalDirpf);
        assertEquals(new BigDecimal("3470.00").add(new BigDecimal("523.61")), judicial);
        assertEquals(new BigDecimal("13968.09"), totalPago);
    }

    @Test
    @DisplayName("Resultado bloco 2 muda quando o informe judicial entra no totalPago")
    void resultadoBloco2_comInformeDiferenteDeSemInforme() {
        IrpfDeclaracaoData dirpf = IrpfDeclaracaoData.builder()
                .anoCalendario("2018")
                .exercicio("2019")
                .tipoTributacao("COMPLETO")
                .rendimentosTributaveisTotal(new BigDecimal("80000.00"))
                .contribuicaoPrevidenciaSocial(new BigDecimal("5000.00"))
                .impostoPagoTotal(new BigDecimal("9974.48"))
                .impostoRestituir(BigDecimal.ZERO)
                .saldoImpostoPagar(new BigDecimal("1000.00"))
                .build();

        ExcelResumoGeralHelper.ResultadoBloco2Simulacao semInforme =
                helper.calcularResultadoBloco2Simulacao(dirpf, BigDecimal.ZERO, faixas2018, params2018, null);
        ExcelResumoGeralHelper.ResultadoBloco2Simulacao comInforme =
                helper.calcularResultadoBloco2Simulacao(
                        dirpf, BigDecimal.ZERO, faixas2018, params2018, informePaulo2018);

        assertNotEquals(semInforme.valorPositivo(), comInforme.valorPositivo());
        // Crédito judicial de 3993,61 aumenta restituição (ou reduz saldo a pagar) em relação ao cenário sem informe
        BigDecimal liquidoSem = semInforme.restituir().subtract(semInforme.saldoPagar());
        BigDecimal liquidoCom = comInforme.restituir().subtract(comInforme.saldoPagar());
        assertEquals(0, liquidoCom.subtract(liquidoSem).compareTo(new BigDecimal("3993.61")));
    }
}
