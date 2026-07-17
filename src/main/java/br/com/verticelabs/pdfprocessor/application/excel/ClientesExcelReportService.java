package br.com.verticelabs.pdfprocessor.application.excel;

import reactor.core.publisher.Mono;

import java.util.List;

public interface ClientesExcelReportService {

    /**
     * Gera o workbook do relatório de clientes.
     *
     * @return bytes do arquivo .xlsx
     */
    Mono<byte[]> generate(List<ClienteExcelReportRow> rows, String filename);
}
