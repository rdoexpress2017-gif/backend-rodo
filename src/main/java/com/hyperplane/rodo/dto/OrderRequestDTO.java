package com.hyperplane.rodo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record OrderRequestDTO(
        @NotBlank String clientId,
        @NotBlank String motoboyId,
        String requesterName,
        @NotNull @Valid AddressDTO originAddress,
        @NotNull @Valid AddressDTO destinationAddress,
        @NotNull @Positive Double routeDistanceKm,
        @NotNull @Positive BigDecimal amount
) {
    public OrderRequestDTO(
            String clientId,
            String motoboyId,
            AddressDTO originAddress,
            AddressDTO destinationAddress,
            Double routeDistanceKm,
            BigDecimal amount
    ) {
        this(clientId, motoboyId, null, originAddress, destinationAddress, routeDistanceKm, amount);
    }
}
