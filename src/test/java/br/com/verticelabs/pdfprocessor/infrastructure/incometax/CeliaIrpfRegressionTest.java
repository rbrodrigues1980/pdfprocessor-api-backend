package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.application.incometax.IrpfDeclaracaoDataMapper;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfSimulacaoMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Célia (AEA): aluguéis (PF titular) fora do total nos simplificados; Imposto devido I = restituir em 2022/2023.
 */
class CeliaIrpfRegressionTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            // ano, PJ titular, PF titular, total, base, imposto devido I, total devido, pago, restituir
            "2016, 67963.80, 6120.00, 74083.80, 59267.04, 5866.11, 5866.11, 7981.24, 2115.13",
            "2017, 73939.20, 9940.63, 83879.83, 67125.49, 8027.18, 8027.18, 9148.74, 1121.56",
            "2018, 75942.60, 10136.25, 86078.85, 69324.51, 8631.92, 8631.92, 10337.88, 1705.96",
            "2019, 79331.04, 6727.21, 86058.25, 69303.91, 8626.25, 8626.25, 11284.20, 2657.95",
            "2022, 101164.32, 8874.00, 110038.32, 90793.21, 14535.81, 14535.81, 17289.48, 2753.67",
            "2023, 108518.16, 5015.00, 113533.16, 93219.30, 15078.17, 15078.17, 19181.04, 4102.87",
            "2024, 113391.72, 9180.00, 122571.72, 105817.38, 18358.79, 18358.79, 18588.04, 229.25",
    })
    void declaracao(String ano, BigDecimal pj, BigDecimal pf, BigDecimal total, BigDecimal base,
            BigDecimal devidoI, BigDecimal totalDevido, BigDecimal pago, BigDecimal restituir) {
        IncomeTaxInfo info = IrpfFixtures.parse("celia-" + ano);
        IrpfDeclaracaoData data = new IrpfDeclaracaoDataMapper().fromIncomeTaxInfo(info);
        SimuladorIrpfRequest request = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator())
                .fromDeclaracao(data, BigDecimal.ZERO, false);

        assertAll(
                () -> assertValor("info total", total, info.getRendimentosTributaveis()),
                () -> assertValor("info PF titular", pf, info.getRendimentosTributaveisTitularPF()),
                () -> assertValor("info imposto devido I", devidoI, info.getImpostoDevidoI()),
                () -> assertValor("info restituir", restituir, info.getImpostoRestituir()),
                () -> assertValor("PJ titular", pj, data.getRendimentosTributaveisTitularPJ()),
                () -> assertValor("PF titular", pf, data.getRendimentosTributaveisTitularPF()),
                () -> assertValor("total", total, data.getRendimentosTributaveisTotal()),
                () -> assertValor("base", base, data.getBaseCalculoImposto()),
                () -> assertValor("imposto devido I", devidoI, data.getImpostoDevidoI()),
                () -> assertValor("total devido", totalDevido, data.getTotalImpostoDevido()),
                () -> assertValor("pago", pago, data.getImpostoPagoTotal()),
                () -> assertValor("restituir", restituir, data.getImpostoRestituir()),
                () -> assertValor("simulação rendimentos", total, request.getRendimentosTributaveis()));
    }

    private static void assertValor(String campo, BigDecimal esperado, BigDecimal atual) {
        assertEquals(0, esperado.compareTo(atual != null ? atual : BigDecimal.ZERO),
                () -> campo + ": esperado " + esperado + ", obtido " + atual);
    }
}
