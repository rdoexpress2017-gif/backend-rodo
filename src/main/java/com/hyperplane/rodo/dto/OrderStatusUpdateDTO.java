package com.hyperplane.rodo.dto;

import com.hyperplane.rodo.model.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record OrderStatusUpdateDTO(
        @NotNull OrderStatus status
) {}
