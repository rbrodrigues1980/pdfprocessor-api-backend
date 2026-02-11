package br.com.verticelabs.pdfprocessor.infrastructure.security;

import br.com.verticelabs.pdfprocessor.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements ReactiveUserDetailsService {

    private final UserRepository userRepository;

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        // Por compatibilidade, tentar buscar por email primeiro, depois por username (legado)
        return userRepository.findByEmail(username)
                .switchIfEmpty(userRepository.findByUsername(username))
                .map(user -> org.springframework.security.core.userdetails.User
                        .withUsername(user.getEmail() != null ? user.getEmail() : user.getNome())
                        .password(user.getSenhaHash() != null ? user.getSenhaHash() : "")
                        .roles(user.getRoles() != null ? user.getRoles().toArray(new String[0]) : new String[] {})
                        .build())
                .switchIfEmpty(Mono.error(new UsernameNotFoundException("User not found: " + username)));
    }
}
