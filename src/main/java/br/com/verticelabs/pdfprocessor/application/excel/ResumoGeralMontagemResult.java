package br.com.verticelabs.pdfprocessor.application.excel;

import br.com.verticelabs.pdfprocessor.application.empresas.EmpresaHonorariosResolver;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelResumoGeralHelper;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelResumoGeralLinhaDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ResumoGeralMontagemResult(
        List<ExcelResumoGeralLinhaDTO> linhas,
        EmpresaHonorariosResolver.HonorariosConfig honorariosConfig,
        ExcelResumoGeralHelper.TotaisResumoGeral totais,
        Map<String, IrpfDeclaracaoData> irpfDeclaracoesAlinhadas,
        Map<String, BigDecimal> prevComplPorAno,
        Map<String, List<IrTabelaTributacao>> tabelasTributacao,
        Map<String, IrParametrosAnuais> parametrosTributacao,
        LocalDate dataPagamentoSelic,
        LocalDateTime dataGeracao) {
}
