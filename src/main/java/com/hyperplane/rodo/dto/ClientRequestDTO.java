package com.hyperplane.rodo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClientRequestDTO(
        @NotBlank String name,
        @NotBlank String document,
        String email,
        @NotBlank String phone,
        @NotNull @Valid AddressDTO baseAddress,
        @NotNull @Valid BillingCycleDTO billingCycle,
        Boolean active
) {}
