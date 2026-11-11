package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.application.incometax.IrpfDeclaracaoDataMapper;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.PagamentoEfetuado;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfSimulacaoMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carmen Lucia AC 2024 — nome de clínica quebrado em 2 linhas fazia
 * {@code CR CLINICA MEDICA ... 800,00} sumir da simulação de despesas médicas.
 */
@DisplayName("Carmen 2024 — despesas médicas nome quebrado")
class Carmen2024DespesasMedicasTest {

    private static final String SECAO_MISTA = """
            PAGAMENTOS EFETUADOS
            Titular
            10 DANIELA CAMPOS MUNIZ MATTOS 520.373.471-20 450,00 0,00
            CONSULTA MEDICA.Descrição:
            21 MARE SAUDE INTEGRADA LTDA 35.455.258/0001-67 7.050,00 0,00
            FISIOTERAPIA.Descrição:
            21 CR CLINICA MEDICA DE GINECOLOGIA E
            OSTETRICIA LTDA
            45.127.670/0001-01 800,00 0,00
            CONSULTA MEDICA DRA. CERES.Descrição:
            21 CEDIPI CLINICA DE DOENCAS
            INFECCIOSAS E IMUNIZACOES LTDA
            01.989.634/0001-81 1.760,00 0,00
            VACINACAO.Descrição:
            21 RAMS ATIVIDADES MEDICAS LTDA 20.247.382/0001-60 800,00 0,00
            CONSULTA.Descrição:
            26 CAIXA ECONOMICA FEDERAL - PLANO
            DE SAUDE
            00.360.305/0001-04 8.739,40 0,00
            PLANO DE SAUDE.Descrição:
            DOAÇÕES EFETUADAS
            Sem Informações
            """;

    @Test
    @DisplayName("normaliza nome multilinha em linha única com CNPJ e valor")
    void normalizaNomeQuebradoCrClinica() {
        String normalizado = ITextIncomeTaxServiceImpl.normalizarSecaoPagamentos(SECAO_MISTA);
        assertTrue(normalizado.contains(
                "21 CR CLINICA MEDICA DE GINECOLOGIA E OSTETRICIA LTDA 45.127.670/0001-01 800,00 0,00"));
        assertTrue(normalizado.contains(
                "21 CEDIPI CLINICA DE DOENCAS INFECCIOSAS E IMUNIZACOES LTDA 01.989.634/0001-81 1.760,00 0,00"));
        assertTrue(normalizado.contains(
                "26 CAIXA ECONOMICA FEDERAL - PLANO DE SAUDE 00.360.305/0001-04 8.739,40 0,00"));
        // inline intacto
        assertTrue(normalizado.contains(
                "21 MARE SAUDE INTEGRADA LTDA 35.455.258/0001-67 7.050,00 0,00"));
    }

    @Test
    @DisplayName("separa código colado no fim da descrição (layout iText)")
    void separaPagamentoColadoAposDescricao() {
        String colado = """
                PAGAMENTOS EFETUADOS
                21 MARE SAUDE INTEGRADA LTDA 35.455.258/0001-67 7.050,00 0,00
                FISIOTERAPIA NF 3789, DE 14/10/2024,21 CR CLINICA MEDICA DE GINECOLOGIA E 45.127.670/0001-01 800,00 0,00
                CONSULTA.Descrição:
                """;
        String normalizado = ITextIncomeTaxServiceImpl.normalizarSecaoPagamentos(colado);
        assertTrue(normalizado.lines().anyMatch(l ->
                        l.trim().startsWith("21 CR CLINICA") && l.contains("800,00")),
                () -> "esperado 21 CR CLINICA em linha própria; got=\n" + normalizado);
    }

    @Test
    @DisplayName("não recoła 2024,\\n21 como se fosse valor monetário")
    void naoRecolaCodigoAposDataComoValor() {
        String comQuebra = "NF 3789, DE 14/10/2024,\n21 CR CLINICA MEDICA 45.127.670/0001-01 800,00 0,00";
        String normalizado = ITextIncomeTaxServiceImpl.normalizarSecaoPagamentos(comQuebra);
        assertTrue(normalizado.lines().anyMatch(l -> l.trim().startsWith("21 CR CLINICA")),
                () -> "21 CR não pode ser colado de volta; got=\n" + normalizado.replace("\n", "\\n"));
        assertTrue(normalizado.contains("3.453,11")
                        || !ITextIncomeTaxServiceImpl.normalizarSecaoPagamentos("3.453, 11").contains("3.453, 11"),
                "regressão: ainda junta valor com espaço");
        assertEquals("3.453,11", ITextIncomeTaxServiceImpl.normalizarSecaoPagamentos("3.453, 11").trim());
    }

    @Test
    @DisplayName("extração inclui CR Clínica 800 e agrega médicas na simulação")
    void extraiCrClinicaEAgregaNaSimulacao() {
        String ident = """
                NOME: CARMEN LUCIA DOMINGUES CUNHA
                CPF: 681.687.996-15
                ANO-CALENDÁRIO 2024 EXERCÍCIO 2025
                DECLARAÇÃO DE AJUSTE ANUAL
                """;
        String resumo = """
                RESUMO
                Despesas médicas
                45.381,40
                """;
        String all = ident + "\n" + SECAO_MISTA + "\n" + resumo;

        IncomeTaxInfo info = new ITextIncomeTaxServiceImpl()
                .parseFromPageTexts(ident, resumo, all);

        List<PagamentoEfetuado> pagamentos = info.getPagamentosEfetuados();
        assertTrue(pagamentos.stream().anyMatch(p ->
                        "21".equals(p.getCodigo())
                                && p.getNomeBeneficiario() != null
                                && p.getNomeBeneficiario().toUpperCase().contains("CR CLINICA")
                                && new BigDecimal("800.00").compareTo(p.getValorPago()) == 0),
                () -> "faltou CR Clínica 800; got=" + descrever(pagamentos));

        assertTrue(pagamentos.stream().anyMatch(p ->
                        "21".equals(p.getCodigo())
                                && p.getNomeBeneficiario() != null
                                && p.getNomeBeneficiario().toUpperCase().contains("CEDIPI")
                                && new BigDecimal("1760.00").compareTo(p.getValorPago()) == 0),
                () -> "faltou CEDIPI; got=" + descrever(pagamentos));

        assertTrue(pagamentos.stream().anyMatch(p ->
                        "21".equals(p.getCodigo())
                                && p.getNomeBeneficiario() != null
                                && p.getNomeBeneficiario().toUpperCase().contains("MARE")
                                && new BigDecimal("7050.00").compareTo(p.getValorPago()) == 0),
                () -> "regressão MARE inline; got=" + descrever(pagamentos));

        IrpfDeclaracaoData data = new IrpfDeclaracaoDataMapper().fromIncomeTaxInfo(info);
        SimuladorIrpfRequest request = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator())
                .fromDeclaracao(data, data.getContribuicaoPrevidenciaPrivada(), true);

        // 450 + 7050 + 800 + 1760 + 800 + 8739,40 = 19.599,40
        assertEquals(0, new BigDecimal("19599.40").compareTo(request.getDespesasMedicas()),
                "simulação médicas; era=" + request.getDespesasMedicas());
    }

    private static String descrever(List<PagamentoEfetuado> pagamentos) {
        return pagamentos.stream()
                .map(p -> p.getCodigo() + "|" + p.getNomeBeneficiario() + "|" + p.getValorPago())
                .reduce((a, b) -> a + "; " + b)
                .orElse("(vazio)");
    }
}
