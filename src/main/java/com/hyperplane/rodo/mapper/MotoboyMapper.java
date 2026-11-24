package com.hyperplane.rodo.mapper;

import com.hyperplane.rodo.dto.MotoboyRequestDTO;
import com.hyperplane.rodo.dto.MotoboyResponseDTO;
import com.hyperplane.rodo.model.Motoboy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MotoboyMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", expression = "java(dto.active() != null ? dto.active() : true)")
    @Mapping(target = "createdAt", expression = "java(java.time.Instant.now())")
    @Mapping(target = "updatedAt", expression = "java(java.time.Instant.now())")
    Motoboy toEntity(MotoboyRequestDTO dto);

    MotoboyResponseDTO toResponseDTO(Motoboy entity);
}
