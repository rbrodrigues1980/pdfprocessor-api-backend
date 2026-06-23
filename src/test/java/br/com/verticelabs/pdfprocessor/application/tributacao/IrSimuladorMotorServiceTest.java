package br.com.verticelabs.pdfprocessor.application.tributacao;



import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfResponse;

import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;

import br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;



import java.math.BigDecimal;

import java.util.ArrayList;

import java.util.List;



import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertTrue;



class IrSimuladorMotorServiceTest {



    private IrSimuladorMotorService motor;

    private List<IrTabelaTributacao> faixas2020;

    private IrParametrosAnuais params2020;



    @BeforeEach

    void setUp() {

        motor = new IrSimuladorMotorService(new IrCalculoProgressivoService(), new IrDoacoesDeducaoCalculator());

        faixas2020 = criarFaixas2016a2022(2020);

        params2020 = IrParametrosAnuais.builder()

                .deducaoDependente(new BigDecimal("2275.08"))

                .limiteInstrucao(new BigDecimal("3561.50"))

                .limiteDescontoSimplificado(new BigDecimal("16754.34"))

                .build();

    }



    @Test

    void elizabeth_ac2020_completo_saldo921() {

        SimuladorIrpfRequest req = SimuladorIrpfRequest.builder()

                .anoCalendario(2020)

                .rendimentosTributaveis(new BigDecimal("86555.68"))

                .previdenciaOficial(new BigDecimal("4580.90"))

                .impostoRetidoFonteTitular(new BigDecimal("11189.14"))

                .build();



        SimuladorIrpfResponse resp = motor.simular(req, faixas2020, params2020);



        assertEquals(new BigDecimal("81974.78"), resp.getModeloCompleto().getBaseCalculo());

        assertEquals(new BigDecimal("12110.74"), resp.getModeloCompleto().getImpostoDevidoFinal());

        assertEquals(new BigDecimal("-921.60"), resp.getModeloCompleto().getSaldo());

        assertEquals(new BigDecimal("921.60"), resp.getModeloCompleto().getSaldoImpostoPagar());



        assertEquals("SIMPLIFICADO", resp.getModeloRecomendado());

        assertTrue(resp.getModeloSimplificado().getSaldo()

                .compareTo(resp.getModeloCompleto().getSaldo()) > 0);

    }



    @Test

    void elizabeth_simplificado_melhorSaldo() {

        SimuladorIrpfRequest req = SimuladorIrpfRequest.builder()

                .anoCalendario(2020)

                .rendimentosTributaveis(new BigDecimal("86555.68"))

                .previdenciaOficial(new BigDecimal("4580.90"))

                .impostoRetidoFonteTitular(new BigDecimal("11189.14"))

                .build();



        SimuladorIrpfResponse resp = motor.simular(req, faixas2020, params2020);



        assertTrue(resp.getModeloSimplificado().getImpostoDevidoFinal()

                .compareTo(resp.getModeloCompleto().getImpostoDevidoFinal()) < 0);

        assertTrue(resp.getModeloSimplificado().getSaldo()

                .compareTo(resp.getModeloCompleto().getSaldo()) > 0);

    }



    @Test

    void pgbl_trava12Porcento() {

        SimuladorIrpfRequest req = SimuladorIrpfRequest.builder()

                .anoCalendario(2020)

                .rendimentosTributaveis(new BigDecimal("100000.00"))

                .previdenciaPrivada(new BigDecimal("20000.00"))

                .build();



        var ded = motor.calcularDeducoesCompleto(req, params2020, new BigDecimal("100000.00"));

        assertEquals(new BigDecimal("12000.00"), ded.total());

    }

    @Test
    void inssDomestico_2018_aplicaLimite() {
        IrParametrosAnuais params = IrParametrosAnuais.builder()
                .limiteInssDomestico(new BigDecimal("1200.32"))
                .build();
        SimuladorIrpfRequest req = SimuladorIrpfRequest.builder()
                .anoCalendario(2018)
                .rendimentosTributaveis(new BigDecimal("100000.00"))
                .previdenciaEmpregadoDomestico(new BigDecimal("1500.00"))
                .build();

        var ded = motor.calcularDeducoesCompleto(req, params, new BigDecimal("100000.00"));
        assertEquals(new BigDecimal("1200.32"), ded.total());
    }

    @Test
    void inssDomestico_2017_abaixoLimite() {
        IrParametrosAnuais params = IrParametrosAnuais.builder()
                .limiteInssDomestico(new BigDecimal("1171.84"))
                .build();
        SimuladorIrpfRequest req = SimuladorIrpfRequest.builder()
                .anoCalendario(2017)
                .rendimentosTributaveis(new BigDecimal("100000.00"))
                .previdenciaEmpregadoDomestico(new BigDecimal("800.00"))
                .build();

        var ded = motor.calcularDeducoesCompleto(req, params, new BigDecimal("100000.00"));
        assertEquals(new BigDecimal("800.00"), ded.total());
    }

    @Test
    void inssDomestico_2019_forcaZero() {
        IrParametrosAnuais params = IrParametrosAnuais.builder()
                .limiteInssDomestico(BigDecimal.ZERO)
                .build();
        SimuladorIrpfRequest req = SimuladorIrpfRequest.builder()
                .anoCalendario(2019)
                .rendimentosTributaveis(new BigDecimal("100000.00"))
                .previdenciaEmpregadoDomestico(new BigDecimal("1500.00"))
                .build();

        var ded = motor.calcularDeducoesCompleto(req, params, new BigDecimal("100000.00"));
        assertEquals(BigDecimal.ZERO.setScale(2), ded.total());
    }

    @Test
    void inssDomestico_naoAfetaSimplificado() {
        IrParametrosAnuais params = IrParametrosAnuais.builder()
                .limiteInssDomestico(new BigDecimal("1200.32"))
                .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                .build();
        SimuladorIrpfRequest semDomestico = SimuladorIrpfRequest.builder()
                .anoCalendario(2018)
                .rendimentosTributaveis(new BigDecimal("100000.00"))
                .build();
        SimuladorIrpfRequest comDomestico = SimuladorIrpfRequest.builder()
                .anoCalendario(2018)
                .rendimentosTributaveis(new BigDecimal("100000.00"))
                .previdenciaEmpregadoDomestico(new BigDecimal("1000.00"))
                .build();

        SimuladorIrpfResponse respSem = motor.simular(semDomestico, faixas2020, params);
        SimuladorIrpfResponse respCom = motor.simular(comDomestico, faixas2020, params);

        assertEquals(respSem.getModeloSimplificado().getTotalDeducoes(),
                respCom.getModeloSimplificado().getTotalDeducoes());
        assertTrue(respCom.getModeloCompleto().getTotalDeducoes()
                .compareTo(respSem.getModeloCompleto().getTotalDeducoes()) > 0);
    }



    @Test

    void educacao_travaPorLimiteAno_titular() {

        SimuladorIrpfRequest req = SimuladorIrpfRequest.builder()

                .anoCalendario(2020)

                .rendimentosTributaveis(new BigDecimal("50000.00"))

                .despesasInstrucaoTitular(new BigDecimal("10000.00"))

                .build();



        var ded = motor.calcularDeducoesCompleto(req, params2020, new BigDecimal("50000.00"));

        assertEquals(new BigDecimal("3561.50"), ded.total());

    }



    @Test

    void educacao_limiteIndividualPorCpf() {

        SimuladorIrpfRequest req = SimuladorIrpfRequest.builder()

                .anoCalendario(2020)

                .rendimentosTributaveis(new BigDecimal("50000.00"))

                .despesasInstrucaoTitular(new BigDecimal("10000.00"))

                .qtdDependentes(1)

                .despesasInstrucaoDependentes(List.of(new BigDecimal("2000.00")))

                .build();



        BigDecimal efetiva = motor.calcularEducacaoEfetiva(req, params2020.getLimiteInstrucao());

        assertEquals(new BigDecimal("5561.50"), efetiva);



        var ded = motor.calcularDeducoesCompleto(req, params2020, new BigDecimal("50000.00"));

        assertEquals(new BigDecimal("7836.58"), ded.total());

    }



    @Test

    void educacao_naoCompartilhaLimite() {

        SimuladorIrpfRequest req = SimuladorIrpfRequest.builder()

                .anoCalendario(2020)

                .rendimentosTributaveis(new BigDecimal("50000.00"))

                .despesasInstrucaoTitular(new BigDecimal("7000.00"))

                .qtdDependentes(1)

                .despesasInstrucaoDependentes(List.of(BigDecimal.ZERO))

                .build();



        BigDecimal efetiva = motor.calcularEducacaoEfetiva(req, params2020.getLimiteInstrucao());

        assertEquals(new BigDecimal("3561.50"), efetiva);

    }



    @Test

    void ac2026_rendimentos50000_impostoZerado() {

        List<IrTabelaTributacao> faixas2026 = criarFaixas2026();

        IrParametrosAnuais params2026 = paramsReducao2026();



        SimuladorIrpfRequest req = SimuladorIrpfRequest.builder()

                .anoCalendario(2026)

                .rendimentosTributaveis(new BigDecimal("50000.00"))

                .build();



        SimuladorIrpfResponse resp = motor.simular(req, faixas2026, params2026);



        assertEquals(BigDecimal.ZERO.setScale(2), resp.getModeloCompleto().getImpostoDevidoFinal());

        assertEquals(BigDecimal.ZERO.setScale(2), resp.getModeloSimplificado().getImpostoDevidoFinal());

    }



    @Test

    void rra_naoAfetaReducao2026() {

        List<IrTabelaTributacao> faixas2026 = criarFaixas2026();

        IrParametrosAnuais params2026 = paramsReducao2026();



        SimuladorIrpfRequest req = SimuladorIrpfRequest.builder()

                .anoCalendario(2026)

                .rendimentosTributaveis(new BigDecimal("50000.00"))

                .rendimentosRRA(new BigDecimal("1000000.00"))

                .impostoDevidoRRA(new BigDecimal("200000.00"))

                .build();



        SimuladorIrpfResponse resp = motor.simular(req, faixas2026, params2026);



        assertEquals(BigDecimal.ZERO.setScale(2), resp.getModeloCompleto().getImpostoDevidoFinal());

        assertEquals(new BigDecimal("200000.00"), resp.getModeloCompleto().getResumo().getTotalImpostoDevido());

        assertEquals(new BigDecimal("1000000.00"), resp.getRendimentosRRA());

    }



    @Test

    void rra_naoEntraNaProgressiva() {

        SimuladorIrpfRequest req = SimuladorIrpfRequest.builder()

                .anoCalendario(2020)

                .rendimentosTributaveis(new BigDecimal("50000.00"))

                .rendimentosRRA(new BigDecimal("500000.00"))

                .build();



        SimuladorIrpfResponse resp = motor.simular(req, faixas2020, params2020);



        assertEquals(new BigDecimal("50000.00"), resp.getModeloCompleto().getBaseCalculo()

                .add(resp.getModeloCompleto().getTotalDeducoes()));

    }



    @Test

    void isento_rendaBaixa() {

        SimuladorIrpfRequest req = SimuladorIrpfRequest.builder()

                .anoCalendario(2020)

                .rendimentosTributaveis(new BigDecimal("20000.00"))

                .build();



        SimuladorIrpfResponse resp = motor.simular(req, faixas2020, params2020);



        assertEquals(BigDecimal.ZERO.setScale(2), resp.getModeloCompleto().getImpostoDevidoFinal());

    }



    @Test

    void restituicao_quandoPagoMaiorQueDevido() {

        SimuladorIrpfRequest req = SimuladorIrpfRequest.builder()

                .anoCalendario(2020)

                .rendimentosTributaveis(new BigDecimal("20000.00"))

                .impostoRetidoFonteTitular(new BigDecimal("500.00"))

                .build();



        SimuladorIrpfResponse resp = motor.simular(req, faixas2020, params2020);



        assertEquals(0, resp.getResumoDeclaracao().getImpostoRestituir().compareTo(new BigDecimal("500.00")));

        assertEquals(0, resp.getResumoDeclaracao().getSaldoImpostoPagar().compareTo(BigDecimal.ZERO));

    }



    @Test
    void doacoes_cap6PctIncentivo() {
        SimuladorIrpfRequest req = SimuladorIrpfRequest.builder()
                .anoCalendario(2020)
                .rendimentosTributaveis(new BigDecimal("86555.68"))
                .previdenciaOficial(new BigDecimal("4580.90"))
                .deducaoIncentivo(new BigDecimal("10000.00"))
                .build();

        SimuladorIrpfResponse resp = motor.simular(req, faixas2020, params2020);

        BigDecimal impostoAposReducao = resp.getModeloCompleto().getImpostoDevidoAposReducao();
        BigDecimal teto6Pct = impostoAposReducao.multiply(new BigDecimal("0.06")).setScale(2, java.math.RoundingMode.HALF_UP);

        assertEquals(teto6Pct, resp.getModeloCompleto().getDeducoesEspeciais());
        assertEquals(impostoAposReducao.subtract(teto6Pct).setScale(2, java.math.RoundingMode.DOWN),
                resp.getModeloCompleto().getImpostoDevidoFinal());
    }

    @Test
    void rogerio_ac2025_completo_truncamentoSerpro() {
        List<IrTabelaTributacao> faixas2025 = criarFaixas2025();
        IrParametrosAnuais params2025 = IrParametrosAnuais.builder()
                .deducaoDependente(new BigDecimal("2275.08"))
                .limiteInstrucao(new BigDecimal("3561.50"))
                .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                .build();

        SimuladorIrpfRequest req = SimuladorIrpfRequest.builder()
                .anoCalendario(2025)
                .rendimentosTributaveis(new BigDecimal("435190.42"))
                .previdenciaOficial(new BigDecimal("24552.09"))
                .previdenciaPrivada(new BigDecimal("47902.41"))
                .qtdDependentes(2)
                .despesasInstrucaoTitular(new BigDecimal("3561.50"))
                .despesasInstrucaoDependentes(List.of(new BigDecimal("3561.50")))
                .despesasMedicas(new BigDecimal("11562.11"))
                .impostoRetidoFonteTitular(new BigDecimal("73350.79"))
                .build();

        SimuladorIrpfResponse resp = motor.simular(req, faixas2025, params2025);

        assertEquals(new BigDecimal("339500.65"), resp.getModeloCompleto().getBaseCalculo());
        assertEquals(new BigDecimal("82508.89"), resp.getModeloCompleto().getImpostoDevidoFinal());
        assertEquals(new BigDecimal("18.95"), resp.getModeloCompleto().getAliquotaEfetiva());
        assertEquals(new BigDecimal("-9158.10"), resp.getModeloCompleto().getSaldo());
        assertEquals(new BigDecimal("9158.10"), resp.getModeloCompleto().getSaldoImpostoPagar());
    }

    private List<IrTabelaTributacao> criarFaixas2025() {
        List<IrTabelaTributacao> faixas = new ArrayList<>();
        faixas.add(faixa(2025, 1, "0", "28467.20", "0", "0", "Isento"));
        faixas.add(faixa(2025, 2, "28467.21", "33919.80", "0.075", "2135.04", "7,5%"));
        faixas.add(faixa(2025, 3, "33919.81", "45012.60", "0.15", "4679.03", "15%"));
        faixas.add(faixa(2025, 4, "45012.61", "55976.16", "0.225", "8054.97", "22,5%"));
        faixas.add(faixa(2025, 5, "55976.17", null, "0.275", "10853.78", "27,5%"));
        return faixas;
    }

    private IrParametrosAnuais paramsReducao2026() {

        return IrParametrosAnuais.builder()

                .deducaoDependente(new BigDecimal("2275.08"))

                .limiteInstrucao(new BigDecimal("3561.50"))

                .limiteDescontoSimplificado(new BigDecimal("17640.00"))

                .reducaoAnualAtiva(true)

                .reducaoRendimentoLimiteIsencao(new BigDecimal("60000.00"))

                .reducaoMaximaCompleta(new BigDecimal("2694.15"))

                .reducaoConstanteLinear(new BigDecimal("8429.73"))

                .reducaoCoeficienteLinear(new BigDecimal("0.095575"))

                .reducaoRendimentoLimiteSuperior(new BigDecimal("88200.00"))

                .build();

    }



    private List<IrTabelaTributacao> criarFaixas2016a2022(int ano) {

        List<IrTabelaTributacao> faixas = new ArrayList<>();

        faixas.add(faixa(ano, 1, "0", "22847.76", "0", "0", "Isento"));

        faixas.add(faixa(ano, 2, "22847.77", "33919.80", "0.075", "1713.58", "7,5%"));

        faixas.add(faixa(ano, 3, "33919.81", "45012.60", "0.15", "4257.57", "15%"));

        faixas.add(faixa(ano, 4, "45012.61", "55976.16", "0.225", "7633.51", "22,5%"));

        faixas.add(faixa(ano, 5, "55976.17", null, "0.275", "10432.32", "27,5%"));

        return faixas;

    }



    private List<IrTabelaTributacao> criarFaixas2026() {

        List<IrTabelaTributacao> faixas = new ArrayList<>();

        faixas.add(faixa(2026, 1, "0", "29145.60", "0", "0", "Isento"));

        faixas.add(faixa(2026, 2, "29145.61", "33919.80", "0.075", "2185.92", "7,5%"));

        faixas.add(faixa(2026, 3, "33919.81", "45012.60", "0.15", "4729.91", "15%"));

        faixas.add(faixa(2026, 4, "45012.61", "55976.16", "0.225", "8105.85", "22,5%"));

        faixas.add(faixa(2026, 5, "55976.17", null, "0.275", "10904.66", "27,5%"));

        return faixas;

    }



    private IrTabelaTributacao faixa(int ano, int n, String inf, String sup, String aliq, String ded, String desc) {

        return IrTabelaTributacao.builder()

                .anoCalendario(ano)

                .tipoIncidencia("ANUAL")

                .faixa(n)

                .limiteInferior(new BigDecimal(inf))

                .limiteSuperior(sup != null ? new BigDecimal(sup) : null)

                .aliquota(new BigDecimal(aliq))

                .deducao(new BigDecimal(ded))

                .descricao(desc)

                .build();

    }

}


