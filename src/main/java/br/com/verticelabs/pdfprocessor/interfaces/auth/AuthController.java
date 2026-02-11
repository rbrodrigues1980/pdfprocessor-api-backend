package br.com.verticelabs.pdfprocessor.interfaces.auth;

import br.com.verticelabs.pdfprocessor.application.auth.*;
import br.com.verticelabs.pdfprocessor.interfaces.auth.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final Verify2FAUseCase verify2FAUseCase;
    private final RefreshUseCase refreshUseCase;
    private final LogoutUseCase logoutUseCase;
    private final RegisterAdminUseCase registerAdminUseCase;
    private final RegisterUserUseCase registerUserUseCase;

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AuthResponse> login(@RequestBody LoginRequest request) {
        log.info("📥 Requisição de login recebida para: {}", request.getEmail());
        return loginUseCase.execute(request)
                .doOnSuccess(response -> log.info("✅ Login processado com sucesso para: {}", request.getEmail()))
                .doOnError(error -> log.error("❌ Erro no login para: {} - {}", request.getEmail(), error.getMessage()));
    }

    @PostMapping("/verify-2fa")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AuthResponse> verify2FA(@RequestBody Verify2FARequest request) {
        return verify2FAUseCase.execute(request.getEmail(), request.getCode());
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AuthResponse> refresh(@RequestBody RefreshRequest request) {
        return refreshUseCase.execute(request.getRefreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> logout(@RequestBody RefreshRequest request) {
        return logoutUseCase.execute(request.getRefreshToken());
    }

    @PostMapping("/register/admin")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<br.com.verticelabs.pdfprocessor.domain.model.User> registerAdmin(@RequestBody RegisterAdminRequest request) {
        return registerAdminUseCase.execute(request.getTenantId(), request.getNome(), request.getEmail(), request.getSenha());
    }

    @PostMapping("/register/user")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<br.com.verticelabs.pdfprocessor.domain.model.User> registerUser(@RequestBody RegisterUserRequest request) {
        return registerUserUseCase.execute(request.getNome(), request.getEmail(), request.getSenha(), request.getRoles());
    }
}
