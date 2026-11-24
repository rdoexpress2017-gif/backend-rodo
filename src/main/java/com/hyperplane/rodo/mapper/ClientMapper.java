package com.hyperplane.rodo.mapper;

import com.hyperplane.rodo.dto.AddressDTO;
import com.hyperplane.rodo.dto.BillingCycleDTO;
import com.hyperplane.rodo.dto.ClientRequestDTO;
import com.hyperplane.rodo.dto.ClientResponseDTO;
import com.hyperplane.rodo.model.Address;
import com.hyperplane.rodo.model.BillingCycle;
import com.hyperplane.rodo.model.Client;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClientMapper {
    Address toAddress(AddressDTO dto);
    AddressDTO toAddressDTO(Address address);

    BillingCycle toBillingCycle(BillingCycleDTO dto);
    BillingCycleDTO toBillingCycleDTO(BillingCycle cycle);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", expression = "java(dto.active() != null ? dto.active() : true)")
    @Mapping(target = "createdAt", expression = "java(java.time.Instant.now())")
    @Mapping(target = "updatedAt", expression = "java(java.time.Instant.now())")
    Client toEntity(ClientRequestDTO dto);

    ClientResponseDTO toResponseDTO(Client entity);
}
