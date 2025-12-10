package br.com.verticelabs.pdfprocessor.domain.service;

import reactor.core.publisher.Mono;

/**
 * Serviço para atualizar planilhas Excel existentes.
 */
public interface ExcelUpdateService {
    
    /**
     * Atualiza o valor de "Imposto Devido" em todas as abas de anos da planilha.
     * O valor é inserido após a linha de totais mensais em cada aba.
     * 
     * @param excelBytes Bytes do arquivo Excel existente (não pode ser null)
     * @param impostoDevido Valor do imposto devido
     * @return Bytes do arquivo Excel atualizado
     */
    Mono<byte[]> updateImpostoDevido(byte[] excelBytes, Double impostoDevido);
}

