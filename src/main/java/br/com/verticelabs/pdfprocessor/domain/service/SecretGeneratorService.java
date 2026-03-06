package br.com.verticelabs.pdfprocessor.domain.service;

import java.util.List;
import java.util.Map;

/**
 * Serviço para geração de chaves criptográficas fortes.
 */
public interface SecretGeneratorService {

    /**
     * Gera uma chave criptográfica forte.
     *
     * @param bits   tamanho em bits (256-4096, múltiplo de 8)
     * @param format formato de saída: base64, base64url, hex
     * @return a chave gerada
     */
    String generate(int bits, String format);

    /**
     * Gera múltiplas chaves criptográficas fortes.
     *
     * @param bits   tamanho em bits
     * @param format formato de saída
     * @param count  quantidade (1-10)
     * @return lista de chaves geradas
     */
    List<String> generateBatch(int bits, String format, int count);

    /**
     * Gera chave com preset para caso de uso específico.
     *
     * @param type tipo: jwt, apikey, refresh, encryption
     * @return mapa com a chave e metadados
     */
    Map<String, Object> generatePreset(String type);

    /**
     * Retorna o nome do algoritmo de entropia em uso.
     */
    String getAlgorithm();

    /**
     * Valida os parâmetros de geração.
     *
     * @throws IllegalArgumentException se inválidos
     */
    void validateParams(int bits, String format, int count);
}
