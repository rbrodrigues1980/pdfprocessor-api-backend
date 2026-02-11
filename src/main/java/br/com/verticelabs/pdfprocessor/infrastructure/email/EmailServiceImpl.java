package br.com.verticelabs.pdfprocessor.infrastructure.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Implementação simples do serviço de email (em produção, usar serviço real como SendGrid, SES, etc.)
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {
    
    @Override
    public Mono<Void> send2FACode(String email, String code) {
        return Mono.fromRunnable(() -> {
            // Em produção, integrar com serviço de email real
            log.info("📧 [2FA] Enviando código {} para {}", code, email);
            // TODO: Implementar envio real de email
            // Exemplo: sendGridService.send(email, "Código de Verificação", "Seu código é: " + code);
        });
    }
    
    @Override
    public Mono<Void> sendWelcomeEmail(String email, String nome) {
        return Mono.fromRunnable(() -> {
            log.info("📧 [Welcome] Enviando email de boas-vindas para {}", email);
            // TODO: Implementar envio real de email
        });
    }
}

