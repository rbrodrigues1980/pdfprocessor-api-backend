package br.com.verticelabs.pdfprocessor.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Parâmetros recomendados OWASP: saltLength=16, hashLength=32, parallelism=4, memory=65536 (64MB), iterations=3
        return new Argon2PasswordEncoder(16, 32, 4, 65536, 3);
    }
}
