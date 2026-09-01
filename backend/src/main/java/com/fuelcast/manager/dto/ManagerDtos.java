package com.fuelcast.manager.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Auth payloads for the station-manager dashboard. */
public final class ManagerDtos {

    private ManagerDtos() { }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
            @NotBlank String displayName) { }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) { }

    public record ManagerResponse(Long id, String email, String displayName) { }

    public record ClaimRequest(@NotNull Long stationId) { }
}
