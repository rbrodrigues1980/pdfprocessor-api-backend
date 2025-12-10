package br.com.verticelabs.pdfprocessor.infrastructure.security;

import br.com.verticelabs.pdfprocessor.infrastructure.config.ApiVersion;
import br.com.verticelabs.pdfprocessor.infrastructure.config.CorsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtService jwtService;
        private final CorsProperties corsProperties;

        @Bean
        public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
                String apiPrefix = ApiVersion.PREFIX; // "/api/v1"
                
                // Criar filtro JWT
                AuthenticationWebFilter jwtFilter = new JwtAuthenticationWebFilter(jwtService);
                
                return http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                                .authorizeExchange(exchanges -> exchanges
                                                .pathMatchers("/v3/api-docs/**",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/webjars/**",
                                                                "/favicon.ico",
                                                                "/error",
                                                                apiPrefix + "/system/**",
                                                                apiPrefix + "/auth/**",
                                                                apiPrefix + "/tenants") // Permitir criação de tenants (apenas SUPER_ADMIN depois)
                                                .permitAll()
                                                .pathMatchers(apiPrefix + "/tenants/**")
                                                .hasRole("SUPER_ADMIN")
                                                .anyExchange().authenticated())
                                .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                                .exceptionHandling(handling -> handling
                                                .authenticationEntryPoint((swe, e) -> {
                                                        return reactor.core.publisher.Mono.fromRunnable(() -> swe
                                                                        .getResponse()
                                                                        .setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED));
                                                }))
                                .build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                
                // Permitir origens configuradas no application.yml
                configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
                
                // Métodos HTTP permitidos
                configuration.setAllowedMethods(Arrays.asList(
                                HttpMethod.GET.name(),
                                HttpMethod.POST.name(),
                                HttpMethod.PUT.name(),
                                HttpMethod.PATCH.name(),
                                HttpMethod.DELETE.name(),
                                HttpMethod.OPTIONS.name()
                ));
                
                // Headers permitidos
                configuration.setAllowedHeaders(Arrays.asList(
                                "Authorization",
                                "Content-Type",
                                "X-Requested-With",
                                "X-Tenant-ID",
                                "Accept",
                                "Origin",
                                "Access-Control-Request-Method",
                                "Access-Control-Request-Headers"
                ));
                
                // Headers expostos para o frontend
                configuration.setExposedHeaders(Arrays.asList(
                                "Authorization",
                                "Content-Type",
                                "Content-Disposition",
                                "X-Tenant-ID"
                ));
                
                // Permitir credenciais (cookies, authorization headers)
                configuration.setAllowCredentials(true);
                
                // Cache da configuração de preflight (OPTIONS) por 1 hora
                configuration.setMaxAge(3600L);
                
                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                
                return source;
        }

        @Bean
        public ReactiveAuthenticationManager reactiveAuthenticationManager(
                        ReactiveUserDetailsService userDetailsService,
                        PasswordEncoder passwordEncoder) {
                var authenticationManager = new UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService);
                authenticationManager.setPasswordEncoder(passwordEncoder);
                return authenticationManager;
        }
}
