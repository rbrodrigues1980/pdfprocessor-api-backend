package br.com.verticelabs.pdfprocessor.interfaces.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private Boolean requires2FA; // true se 2FA for necessário
    private String message; // Mensagem para o usuário (ex: "Código enviado por e-mail")
}
