package com.hyperplane.rodo.dto;

import jakarta.validation.constraints.NotBlank;

public record AddressDTO(
        @NotBlank String street,
        @NotBlank String number,
        String neighborhood,
        @NotBlank String city,
        @NotBlank String state,
        String zipCode,
        Double lat,
        Double lng
) {}
