package com.hyperplane.rodo.mapper;

import com.hyperplane.rodo.dto.PaymentOrderResponseDTO;
import com.hyperplane.rodo.model.PaymentOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentOrderMapper {
    @Mapping(target = "clientName", ignore = true)
    PaymentOrderResponseDTO toResponseDTO(PaymentOrder entity);
}
