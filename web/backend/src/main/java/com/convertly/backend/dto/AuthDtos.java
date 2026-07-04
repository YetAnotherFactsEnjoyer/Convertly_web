package com.convertly.backend.dto;

import com.convertly.backend.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 120) String displayName
    ) {
    }

    public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password
    ) {
    }

    public record UserResponse(
        UUID id,
        String email,
        String displayName,
        User.Role role,
        Instant createdAt
    ) {
        public static UserResponse from(User user) {
            return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.getCreatedAt()
            );
        }
    }

    public record AuthResponse(
        UserResponse user,
        String token,
        Instant expiresAt
    ) {
        public static AuthResponse from(User user, String token, Instant expiresAt) {
            return new AuthResponse(UserResponse.from(user), token, expiresAt);
        }
    }
}
