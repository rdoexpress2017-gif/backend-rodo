package com.hyperplane.rodo.dto;

import java.time.Instant;

public record ErrorResponseDTO(
        int status,
        String message,
        Instant timestamp
) {}
