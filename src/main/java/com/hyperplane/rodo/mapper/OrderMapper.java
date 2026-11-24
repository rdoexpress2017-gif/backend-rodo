package com.hyperplane.rodo.mapper;

import com.hyperplane.rodo.dto.AddressDTO;
import com.hyperplane.rodo.dto.OrderRequestDTO;
import com.hyperplane.rodo.dto.OrderResponseDTO;
import com.hyperplane.rodo.model.Address;
import com.hyperplane.rodo.model.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    Address toAddress(AddressDTO dto);
    AddressDTO toAddressDTO(Address address);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orderNumber", ignore = true)
    @Mapping(target = "createdByUserId", ignore = true)
    @Mapping(target = "motoboyCommissionAmount", ignore = true)
    @Mapping(target = "status", constant = "PENDENTE")
    @Mapping(target = "paymentOrderId", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.Instant.now())")
    @Mapping(target = "collectedAt", ignore = true)
    @Mapping(target = "deliveredAt", ignore = true)
    @Mapping(target = "paidAt", ignore = true)
    @Mapping(target = "updatedAt", expression = "java(java.time.Instant.now())")
    Order toEntity(OrderRequestDTO dto);

    @Mapping(target = "clientName", ignore = true)
    @Mapping(target = "motoboyName", ignore = true)
    @Mapping(target = "createdByUserName", ignore = true)
    OrderResponseDTO toResponseDTO(Order entity);
}
