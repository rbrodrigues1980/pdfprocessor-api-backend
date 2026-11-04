package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.application.incometax.IrpfDeclaracaoDataMapper;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrCalculoProgressivoService;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrDoacoesDeducaoCalculator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrSimuladorMotorService;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfDeducoesResumoDTO;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfDeducoesResumoHelper;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfSimulacaoMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Luciano AC 2016: pensão por escritura pública (cód. 33) não pode duplicar na linha judicial.
 */
class Luciano2016PensaoEscrituraTest {

    @Test
    void escrituraNaoDuplicaNaPensaoJudicial() {
        IncomeTaxInfo info = IrpfFixtures.parse("luciano-2016");

        assertValor("0.00", info.getDeducoesPensaoJudicial());
        assertValor("55886.36", info.getDeducoesPensaoEscritura());
        assertValor("1043.05", info.getDeducaoIncentivo());
        assertValor("20259.26", info.getImpostoDevidoI());
        assertValor("4829.94", info.getSaldoImpostoPagar());

        IrpfDeclaracaoData data = new IrpfDeclaracaoDataMapper().fromIncomeTaxInfo(info);
        IrPagamentosDeducaoAggregator aggregator = new IrPagamentosDeducaoAggregator();
        SimuladorIrpfRequest request = new ExcelIrpfSimulacaoMapper(aggregator)
                .fromDeclaracao(data, BigDecimal.ZERO, false);
        ExcelIrpfDeducoesResumoDTO deducoes = new ExcelIrpfDeducoesResumoHelper(
                new IrSimuladorMotorService(new IrCalculoProgressivoService(), new IrDoacoesDeducaoCalculator()),
                aggregator).montar(data, request, BigDecimal.ZERO, null);

        assertValor("0.00", deducoes.getPensaoJudicial());
        assertValor("55886.36", deducoes.getPensaoEscritura());
        assertValor("55886.36", request.getPensaoAlimenticia());
    }

    private static void assertValor(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual != null ? atual : BigDecimal.ZERO),
                () -> "esperado " + esperado + ", obtido " + atual);
    }
}
