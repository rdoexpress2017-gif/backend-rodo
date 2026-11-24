package com.hyperplane.rodo.dto;

import java.util.List;

public record ReportDetailResponseDTO(
        ReportResponseDTO report,
        List<OrderResponseDTO> orders
) {}
