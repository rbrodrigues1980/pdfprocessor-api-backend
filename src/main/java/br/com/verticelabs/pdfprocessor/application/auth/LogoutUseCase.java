package br.com.verticelabs.pdfprocessor.application.auth;

import br.com.verticelabs.pdfprocessor.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Use case para logout (invalida refresh token)
 */
@Service
@RequiredArgsConstructor
public class LogoutUseCase {
    
    private final UserRepository userRepository;
    
    public Mono<Void> execute(String refreshToken) {
        return userRepository.findByRefreshToken(refreshToken)
                .flatMap(user -> {
                    if (user.getRefreshTokens() != null) {
                        user.getRefreshTokens().removeIf(rt -> rt.getToken().equals(refreshToken));
                        return userRepository.save(user);
                    }
                    return Mono.just(user);
                })
                .then();
    }
}

