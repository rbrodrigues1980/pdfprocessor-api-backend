package br.com.verticelabs.pdfprocessor.infrastructure.email;

import reactor.core.publisher.Mono;

/**
 * Interface do serviço de email (domínio)
 */
public interface EmailService {
    /**
     * Envia código 2FA por email
     */
    Mono<Void> send2FACode(String email, String code);
    
    /**
     * Envia email de boas-vindas
     */
    Mono<Void> sendWelcomeEmail(String email, String nome);
}

