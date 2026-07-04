package com.convertly.backend.service;

import com.convertly.backend.entity.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final Duration expiresIn;

    public JwtService(
        ObjectMapper objectMapper,
        @Value("${convertly.jwt.secret}") String secret,
        @Value("${convertly.jwt.expires-in}") Duration expiresIn
    ) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("convertly.jwt.secret must contain at least 32 characters");
        }

        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expiresIn = expiresIn;
    }

    public GeneratedToken generate(User user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(expiresIn);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", user.getEmail());
        claims.put("uid", user.getId().toString());
        claims.put("role", user.getRole().name());
        claims.put("iat", issuedAt.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());

        String unsignedToken = encodeJson(header) + "." + encodeJson(claims);
        String signature = sign(unsignedToken);
        return new GeneratedToken(unsignedToken + "." + signature, expiresAt);
    }

    public String validateAndGetSubject(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");

        if (parts.length != 3) {
            throw new JwtAuthenticationException("Invalid authentication token");
        }

        String unsignedToken = parts[0] + "." + parts[1];
        String expectedSignature = sign(unsignedToken);

        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new JwtAuthenticationException("Invalid authentication token");
        }

        Map<String, Object> header = decodeJson(parts[0]);
        if (!"HS256".equals(header.get("alg"))) {
            throw new JwtAuthenticationException("Invalid authentication token");
        }

        Map<String, Object> claims = decodeJson(parts[1]);
        String subject = claims.get("sub") instanceof String value ? value : null;
        long expiresAt = getLongClaim(claims, "exp");

        if (subject == null || subject.isBlank()) {
            throw new JwtAuthenticationException("Invalid authentication token");
        }
        if (expiresAt <= Instant.now().getEpochSecond()) {
            throw new JwtAuthenticationException("Authentication token expired");
        }

        return subject;
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encode authentication token", exception);
        }
    }

    private Map<String, Object> decodeJson(String value) {
        try {
            return objectMapper.readValue(BASE64_URL_DECODER.decode(value), new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new JwtAuthenticationException("Invalid authentication token");
        }
    }

    private long getLongClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);

        if (value instanceof Number number) {
            return number.longValue();
        }

        throw new JwtAuthenticationException("Invalid authentication token");
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign authentication token", exception);
        }
    }

    public record GeneratedToken(String token, Instant expiresAt) {
    }

    public static class JwtAuthenticationException extends RuntimeException {
        public JwtAuthenticationException(String message) {
            super(message);
        }
    }
}
