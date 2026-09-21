package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.application.incometax.IrpfDeclaracaoDataMapper;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.DoacaoEfetuada;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfSimulacaoMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Helena de Sá Araújo AC 2023 — doações diretas ECA (40) + Pessoa Idosa (43)
 * conferidas com o RESUMO (543,48). A simulação não pode usar só o ECA (271,74).
 */
class Helena2023DoacaoEcaIdosoTest {

    private static final Path PDF_COPIA = Paths.get("temp/_helena_araujo/declaracao_2023.pdf");
    private static final Path PDF_ORIG = Paths.get(
            "temp/PASTADOCUMENTOS/Maria Helena de Sá Araujo - AEAP PE",
            "DeclaraçãodeImpostodeRenda_2023.pdf");

    private static final BigDecimal ECA = new BigDecimal("271.74");
    private static final BigDecimal IDOSO = new BigDecimal("271.74");
    private static final BigDecimal RESUMO = new BigDecimal("543.48");

    @Test
    void extraiEcaEPessoaIdosaEConfereComResumo() throws Exception {
        Path pdf = resolverPdf();
        Assumptions.assumeTrue(pdf != null && Files.exists(pdf), "PDF Helena 2023 não encontrado");

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(new FileInputStream(pdf.toFile())).block();
        assertNotNull(info);

        List<DoacaoEfetuada> doacoes = info.getDoacoesEfetuadas();
        assertNotNull(doacoes);

        DoacaoEfetuada eca = doacoes.stream()
                .filter(d -> "40".equals(d.getCodigo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Doação ECA cód. 40 não encontrada: " + doacoes));
        DoacaoEfetuada idoso = doacoes.stream()
                .filter(d -> "43".equals(d.getCodigo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Doação Pessoa Idosa cód. 43 não encontrada: " + doacoes));

        assertEquals("18.620.890/0001-08", eca.getCpfCnpj());
        assertEquals(0, ECA.compareTo(eca.getValorDoado()));
        assertEquals("29.234.014/0001-51", idoso.getCpfCnpj());
        assertEquals(0, IDOSO.compareTo(idoso.getValorDoado()));
        assertEquals(0, RESUMO.compareTo(info.getDeducaoIncentivo()),
                "RESUMO Dedução de incentivo deve ser ECA + Idoso");
        assertEquals(0, new BigDecimal("8514.83").compareTo(info.getImpostoDevidoI()));
        assertEquals(0, new BigDecimal("9058.31").compareTo(info.getImpostoDevido()));

        IrpfDeclaracaoData data = new IrpfDeclaracaoDataMapper().fromIncomeTaxInfo(info);
        ExcelIrpfSimulacaoMapper excelMapper = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator());
        SimuladorIrpfRequest request = excelMapper.fromDeclaracao(
                data, data.getContribuicaoPrevidenciaPrivada(), true);
        assertEquals(0, RESUMO.compareTo(request.getDeducaoIncentivo()),
                "Simulação deve usar ECA + Idoso (conferido com RESUMO), não só 271,74");
    }

    @Test
    void simulacaoUsaResumoQuandoGranularSoTemEca() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .anoCalendario("2023")
                .tipoTributacao("COMPLETO")
                .deducaoIncentivo(RESUMO)
                .doacoesEfetuadas(List.of(
                        IrpfDeclaracaoData.DoacaoEfetuadaIrpf.builder()
                                .codigo("40")
                                .nomeBeneficiario("Municipal - SP - SÃO PAULO - CACHOEIRA PAULISTA")
                                .cpfCnpj("18.620.890/0001-08")
                                .valorDoado(ECA)
                                .build()))
                .build();

        ExcelIrpfSimulacaoMapper mapper = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator());
        SimuladorIrpfRequest request = mapper.fromDeclaracao(data, BigDecimal.ZERO);
        assertEquals(0, RESUMO.compareTo(request.getDeducaoIncentivo()),
                "Se a extração granular perdeu o Idoso, o RESUMO 543,48 prevalece sobre 271,74");
    }

    @Test
    void simulacaoNaoTrocaIncentivoPorImpostoDevidoIDoResumo() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .anoCalendario("2023")
                .tipoTributacao("COMPLETO")
                .deducaoIncentivo(new BigDecimal("8514.83"))
                .impostoDevidoI(new BigDecimal("8514.83"))
                .totalImpostoDevido(new BigDecimal("8514.83"))
                .doacoesEfetuadas(List.of(
                        IrpfDeclaracaoData.DoacaoEfetuadaIrpf.builder()
                                .codigo("40")
                                .cpfCnpj("18.620.890/0001-08")
                                .valorDoado(ECA)
                                .build(),
                        IrpfDeclaracaoData.DoacaoEfetuadaIrpf.builder()
                                .codigo("43")
                                .cpfCnpj("29.234.014/0001-51")
                                .valorDoado(IDOSO)
                                .build()))
                .build();

        ExcelIrpfSimulacaoMapper mapper = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator());
        SimuladorIrpfRequest request = mapper.fromDeclaracao(data, BigDecimal.ZERO);
        assertEquals(0, RESUMO.compareTo(request.getDeducaoIncentivo()),
                "RESUMO 8.514,83 igual ao Imposto devido I não pode substituir ECA+Idoso 543,48");
    }

    private static Path resolverPdf() {
        if (Files.exists(PDF_COPIA)) {
            return PDF_COPIA;
        }
        if (Files.exists(PDF_ORIG)) {
            return PDF_ORIG;
        }
        return PDF_COPIA;
    }
}
