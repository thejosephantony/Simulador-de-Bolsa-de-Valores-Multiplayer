package br.ufs.stockmarket.auth.api;

import br.ufs.stockmarket.auth.domain.UserEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        OffsetDateTime createdAt
) {
    public static UserResponse from(UserEntity user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getCreatedAt());
    }
}
