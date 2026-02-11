package br.com.verticelabs.pdfprocessor.application.auth;

import br.com.verticelabs.pdfprocessor.domain.exceptions.Invalid2FACodeException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.InvalidCredentialsException;
import br.com.verticelabs.pdfprocessor.domain.model.User;
import br.com.verticelabs.pdfprocessor.domain.repository.UserRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.JwtService;
import br.com.verticelabs.pdfprocessor.interfaces.auth.dto.AuthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Use case para verificar código 2FA e completar login
 */
@Service
@RequiredArgsConstructor
public class Verify2FAUseCase {
    
    private final UserRepository userRepository;
    private final JwtService jwtService;
    
    public Mono<AuthResponse> execute(String email, String code) {
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new InvalidCredentialsException("Usuário não encontrado")))
                .flatMap(user -> {
                    // Validar código 2FA
                    if (user.getTwoFactorTempCode() == null || 
                        !user.getTwoFactorTempCode().equals(code)) {
                        return Mono.error(new Invalid2FACodeException("Código 2FA inválido"));
                    }
                    
                    if (user.getTwoFactorTempCodeExpires() == null ||
                        user.getTwoFactorTempCodeExpires().isBefore(Instant.now())) {
                        return Mono.error(new Invalid2FACodeException("Código 2FA expirado"));
                    }
                    
                    // Limpar código 2FA
                    user.setTwoFactorTempCode(null);
                    user.setTwoFactorTempCodeExpires(null);
                    
                    // Gerar tokens
                    String accessToken = jwtService.generateToken(
                            user.getId(),
                            user.getEmail(),
                            user.getTenantId(),
                            user.getRoles()
                    );
                    
                    String refreshToken = UUID.randomUUID().toString();
                    User.RefreshToken refreshTokenObj = User.RefreshToken.builder()
                            .token(refreshToken)
                            .createdAt(Instant.now())
                            .expiresAt(Instant.now().plusSeconds(2592000)) // 30 dias
                            .used(false)
                            .build();
                    
                    user.getRefreshTokens().add(refreshTokenObj);
                    
                    return userRepository.save(user)
                            .then(Mono.just(AuthResponse.builder()
                                    .accessToken(accessToken)
                                    .refreshToken(refreshToken)
                                    .build()));
                });
    }
}

