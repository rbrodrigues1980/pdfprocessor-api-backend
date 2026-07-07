package br.com.verticelabs.pdfprocessor.application.security;

import br.com.verticelabs.pdfprocessor.domain.exceptions.ForbiddenOperationException;
import br.com.verticelabs.pdfprocessor.domain.repository.UserRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;

/**
 * Controle de acesso do perfil EVALUATOR (avaliador): restringe o acesso apenas
 * aos clientes (person IDs) atribuídos pelo SUPER_ADMIN na allowlist do usuário.
 *
 * <p>A allowlist é lida do banco (fonte da verdade), de modo que reatribuições do
 * SUPER_ADMIN passam a valer imediatamente, sem exigir novo login.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluatorAccessService {

    private final UserRepository userRepository;

    /** {@code true} quando o usuário autenticado tem a role EVALUATOR. */
    public Mono<Boolean> isEvaluator() {
        return ReactiveSecurityContextHelper.isEvaluator();
    }

    /**
     * Allowlist de person IDs do avaliador atual. Vazia quando o usuário não é
     * avaliador ou quando nenhum cliente foi atribuído.
     */
    public Mono<Set<String>> currentAllowedPersonIds() {
        return ReactiveSecurityContextHelper.isEvaluator()
                .flatMap(isEvaluator -> {
                    if (!Boolean.TRUE.equals(isEvaluator)) {
                        return Mono.just(new HashSet<String>());
                    }
                    return ReactiveSecurityContextHelper.getUserId()
                            .flatMap(userRepository::findById)
                            .map(user -> user.getAllowedPersonIds() != null
                                    ? new HashSet<>(user.getAllowedPersonIds())
                                    : new HashSet<String>())
                            .defaultIfEmpty(new HashSet<>());
                });
    }

    /**
     * Garante que o avaliador atual pode acessar a pessoa informada.
     * No-op para as demais roles (SUPER_ADMIN, TENANT_ADMIN, TENANT_USER).
     *
     * @throws ForbiddenOperationException quando o avaliador não tem o cliente na allowlist.
     */
    public Mono<Void> assertPersonAccessible(String personId) {
        return ReactiveSecurityContextHelper.isEvaluator()
                .flatMap(isEvaluator -> {
                    if (!Boolean.TRUE.equals(isEvaluator)) {
                        return Mono.<Void>empty();
                    }
                    return currentAllowedPersonIds().flatMap(allowed -> {
                        if (personId != null && allowed.contains(personId)) {
                            return Mono.<Void>empty();
                        }
                        log.warn("🚫 Avaliador sem acesso ao cliente {}", personId);
                        return Mono.<Void>error(new ForbiddenOperationException(
                                "Você não tem acesso a este cliente."));
                    });
                })
                .then();
    }
}
