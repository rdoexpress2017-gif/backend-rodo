package com.hyperplane.rodo.mapper;

import com.hyperplane.rodo.dto.ExpenseRequestDTO;
import com.hyperplane.rodo.dto.ExpenseResponseDTO;
import com.hyperplane.rodo.model.Expense;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ExpenseMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.Instant.now())")
    @Mapping(target = "updatedAt", expression = "java(java.time.Instant.now())")
    Expense toEntity(ExpenseRequestDTO dto);

    ExpenseResponseDTO toResponseDTO(Expense entity);
}
