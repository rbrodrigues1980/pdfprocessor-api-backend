package br.com.verticelabs.pdfprocessor.application.auth;

import br.com.verticelabs.pdfprocessor.domain.exceptions.InvalidRefreshTokenException;
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
 * Use case para refresh token rotativo
 */
@Service
@RequiredArgsConstructor
public class RefreshUseCase {
    
    private final UserRepository userRepository;
    private final JwtService jwtService;
    
    public Mono<AuthResponse> execute(String refreshToken) {
        return userRepository.findByRefreshToken(refreshToken)
                .switchIfEmpty(Mono.error(new InvalidRefreshTokenException("Refresh token inválido ou expirado")))
                .flatMap(user -> {
                    // Encontrar o refresh token usado
                    User.RefreshToken usedToken = user.getRefreshTokens().stream()
                            .filter(rt -> rt.getToken().equals(refreshToken))
                            .findFirst()
                            .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token não encontrado"));
                    
                    // Verificar se já foi usado (rotativo)
                    if (Boolean.TRUE.equals(usedToken.getUsed())) {
                        // Logout global - token foi reutilizado
                        user.getRefreshTokens().clear();
                        return userRepository.save(user)
                                .then(Mono.error(new InvalidRefreshTokenException("Refresh token já foi usado. Logout global por segurança.")));
                    }
                    
                    // Marcar como usado
                    usedToken.setUsed(true);
                    
                    // Gerar novos tokens
                    String newAccessToken = jwtService.generateToken(
                            user.getId(),
                            user.getEmail(),
                            user.getTenantId(),
                            user.getRoles()
                    );
                    
                    String newRefreshToken = UUID.randomUUID().toString();
                    User.RefreshToken newRefreshTokenObj = User.RefreshToken.builder()
                            .token(newRefreshToken)
                            .createdAt(Instant.now())
                            .expiresAt(Instant.now().plusSeconds(2592000)) // 30 dias
                            .used(false)
                            .build();
                    
                    user.getRefreshTokens().add(newRefreshTokenObj);
                    
                    // Limpar tokens expirados
                    user.getRefreshTokens().removeIf(rt -> rt.getExpiresAt().isBefore(Instant.now()));
                    
                    return userRepository.save(user)
                            .then(Mono.just(AuthResponse.builder()
                                    .accessToken(newAccessToken)
                                    .refreshToken(newRefreshToken)
                                    .build()));
                });
    }
}

