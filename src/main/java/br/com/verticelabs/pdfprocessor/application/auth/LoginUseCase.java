package br.com.verticelabs.pdfprocessor.application.auth;

import br.com.verticelabs.pdfprocessor.domain.exceptions.InvalidCredentialsException;
import br.com.verticelabs.pdfprocessor.domain.model.Tenant;
import br.com.verticelabs.pdfprocessor.domain.model.User;
import br.com.verticelabs.pdfprocessor.domain.repository.TenantRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.UserRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.email.EmailService;
import br.com.verticelabs.pdfprocessor.infrastructure.security.JwtService;
import br.com.verticelabs.pdfprocessor.interfaces.auth.dto.AuthResponse;
import br.com.verticelabs.pdfprocessor.interfaces.auth.dto.LoginRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;

/**
 * Use case para login com suporte a 2FA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginUseCase {
    
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    
    @Value("${app.security.force2fa-global:false}")
    private Boolean force2faGlobal;
    
    public Mono<AuthResponse> execute(LoginRequest request) {
        log.info("🔐 Tentativa de login para email: {}", request.getEmail());
        return userRepository.findByEmail(request.getEmail())
                .doOnNext(user -> log.debug("👤 Usuário encontrado: {} (tenantId: {})", user.getEmail(), user.getTenantId()))
                .filter(user -> passwordEncoder.matches(request.getPassword(), user.getSenhaHash()))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("❌ Credenciais inválidas para email: {}", request.getEmail());
                    return Mono.error(new InvalidCredentialsException("Credenciais inválidas"));
                }))
                .filter(User::getAtivo)
                .switchIfEmpty(Mono.error(new RuntimeException("Usuário está inativo")))
                .flatMap(user -> {
                    // Verificar se tenant está ativo (se não for SUPER_ADMIN)
                    if (user.getTenantId() != null) {
                        return tenantRepository.findById(user.getTenantId())
                                .filter(Tenant::getAtivo)
                                .switchIfEmpty(Mono.error(new RuntimeException("Tenant está inativo")))
                                .then(Mono.just(user));
                    }
                    return Mono.just(user);
                })
                .flatMap(user -> {
                    // Verificar se 2FA é necessário
                    if (user.getTenantId() != null) {
                        return tenantRepository.findById(user.getTenantId())
                                .map(tenant -> {
                                    boolean tenantRequires2FA = tenant.getConfig() != null && 
                                            Boolean.TRUE.equals(tenant.getConfig().getTwoFactorRequired());
                                    return force2faGlobal || tenantRequires2FA || Boolean.TRUE.equals(user.getTwoFactorEnabled());
                                })
                                .defaultIfEmpty(force2faGlobal || Boolean.TRUE.equals(user.getTwoFactorEnabled()))
                                .flatMap(requires2FA -> process2FA(user, requires2FA));
                    } else {
                        // SUPER_ADMIN
                        boolean requires2FA = force2faGlobal || Boolean.TRUE.equals(user.getTwoFactorEnabled());
                        return process2FA(user, requires2FA);
                    }
                });
    }
    
    private Mono<AuthResponse> process2FA(User user, boolean requires2FA) {
                    
        if (requires2FA) {
            // Gerar código 2FA
            String code = generate2FACode();
            user.setTwoFactorTempCode(code);
            user.setTwoFactorTempCodeExpires(Instant.now().plusSeconds(300)); // 5 minutos
            
            return userRepository.save(user)
                    .then(emailService.send2FACode(user.getEmail(), code))
                    .then(Mono.just(AuthResponse.builder()
                            .requires2FA(true)
                            .message("Código de verificação enviado por e-mail")
                            .build()));
        } else {
            // Login direto sem 2FA
            return generateTokens(user);
        }
    }
    
    private Mono<AuthResponse> generateTokens(User user) {
        log.info("🎫 Gerando tokens para usuário: {} (roles: {})", user.getEmail(), user.getRoles());
        String accessToken = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                user.getTenantId(),
                user.getRoles()
        );
        
        // Gerar refresh token
        String refreshToken = UUID.randomUUID().toString();
        User.RefreshToken refreshTokenObj = User.RefreshToken.builder()
                .token(refreshToken)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(2592000)) // 30 dias
                .used(false)
                .build();
        
        user.getRefreshTokens().add(refreshTokenObj);
        
        return userRepository.save(user)
                .doOnSuccess(u -> log.info("✅ Login bem-sucedido para: {} (tenantId: {})", u.getEmail(), u.getTenantId()))
                .then(Mono.just(AuthResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .build()));
    }
    
    private String generate2FACode() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(1000000));
    }
}

