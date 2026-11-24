package com.hyperplane.rodo.dto;

import java.time.Instant;

public record ClientResponseDTO(
        String id,
        String name,
        String document,
        String email,
        String phone,
        AddressDTO baseAddress,
        BillingCycleDTO billingCycle,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
