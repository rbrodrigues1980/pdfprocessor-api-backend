package br.com.verticelabs.pdfprocessor.infrastructure.config;

/**
 * Constante centralizada para a versão da API.
 * Facilita a manutenção e suporte a múltiplas versões no futuro.
 */
public final class ApiVersion {
    
    /**
     * Versão atual da API (ex: "v1", "v2", etc.)
     */
    public static final String CURRENT = "v1";
    
    /**
     * Prefixo completo da API (ex: "/api/v1")
     */
    public static final String PREFIX = "/api/" + CURRENT;
    
    private ApiVersion() {
        // Classe utilitária - não deve ser instanciada
    }
}

