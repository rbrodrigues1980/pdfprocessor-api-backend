package br.com.verticelabs.pdfprocessor.infrastructure.security;

import br.com.verticelabs.pdfprocessor.domain.service.SecretGeneratorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/**
 * Implementação do gerador de chaves criptográficas usando {@link SecureRandom}.
 *
 * <p>Tenta usar o algoritmo DRBG (Deterministic Random Bit Generator, NIST SP 800-90A)
 * para máxima segurança. Se não disponível, usa o melhor algoritmo do SO.</p>
 */
@Slf4j
@Service
public class SecretGeneratorServiceImpl implements SecretGeneratorService {

    private static final SecureRandom SECURE_RANDOM = createSecureRandom();

    private static final Map<String, Object> RECOMMENDATIONS = Map.of(
            "jwt_secret", "Mínimo 256 bits (HS256), recomendado 512 bits",
            "api_key", "Recomendado 256 bits em base64url",
            "refresh_token", "Recomendado 512 bits em base64url",
            "encryption_key_aes256", "Exatamente 256 bits"
    );

    private static final Map<String, int[]> PRESETS = Map.of(
            "jwt", new int[]{512, 0},       // 0 = base64url
            "apikey", new int[]{256, 0},     // 0 = base64url
            "refresh", new int[]{512, 0},    // 0 = base64url
            "encryption", new int[]{256, 1}  // 1 = hex
    );

    private static final String[] FORMAT_NAMES = {"base64url", "hex", "base64"};

    private static SecureRandom createSecureRandom() {
        try {
            SecureRandom sr = SecureRandom.getInstance("DRBG");
            log.info("SecretGeneratorService usando algoritmo DRBG (NIST SP 800-90A)");
            return sr;
        } catch (Exception e) {
            log.info("DRBG não disponível, usando SecureRandom padrão do SO");
            return new SecureRandom();
        }
    }

    @Override
    public String generate(int bits, String format) {
        byte[] bytes = new byte[bits / 8];
        SECURE_RANDOM.nextBytes(bytes);

        return switch (normalizeFormat(format)) {
            case "base64" -> Base64.getEncoder().withoutPadding().encodeToString(bytes);
            case "hex" -> HexFormat.of().formatHex(bytes);
            default -> Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        };
    }

    @Override
    public List<String> generateBatch(int bits, String format, int count) {
        List<String> secrets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            secrets.add(generate(bits, format));
        }
        return secrets;
    }

    @Override
    public Map<String, Object> generatePreset(String type) {
        int[] config = PRESETS.get(type.toLowerCase());
        if (config == null) {
            throw new IllegalArgumentException(
                    "Tipo inválido: '" + type + "'. Use: jwt, apikey, refresh, encryption");
        }

        int bits = config[0];
        String format = FORMAT_NAMES[config[1]];
        String secret = generate(bits, format);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", type.toLowerCase());
        response.put("secret", secret);
        response.put("algorithm", SECURE_RANDOM.getAlgorithm());
        response.put("bits", bits);
        response.put("bytes", bits / 8);
        response.put("format", format);
        response.put("generatedAt", Instant.now().toString());
        response.put("recommendations", RECOMMENDATIONS);
        return response;
    }

    @Override
    public String getAlgorithm() {
        return SECURE_RANDOM.getAlgorithm();
    }

    @Override
    public void validateParams(int bits, String format, int count) {
        if (bits < 256 || bits > 4096) {
            throw new IllegalArgumentException("Tamanho deve ser entre 256 e 4096 bits");
        }
        if (bits % 8 != 0) {
            throw new IllegalArgumentException("Tamanho deve ser múltiplo de 8");
        }
        if (count < 1 || count > 10) {
            throw new IllegalArgumentException("Quantidade deve ser entre 1 e 10");
        }
    }

    private String normalizeFormat(String format) {
        if (format == null) return "base64url";
        return switch (format.toLowerCase()) {
            case "base64", "base64url", "hex" -> format.toLowerCase();
            default -> "base64url";
        };
    }
}
