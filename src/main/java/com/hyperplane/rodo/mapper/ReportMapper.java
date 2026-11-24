package com.hyperplane.rodo.mapper;

import com.hyperplane.rodo.dto.ReportResponseDTO;
import com.hyperplane.rodo.model.Report;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReportMapper {
    @Mapping(target = "targetEntityName", ignore = true)
    @Mapping(target = "targetEntitySubtype", ignore = true)
    @Mapping(target = "paymentOrderId", ignore = true)
    @Mapping(target = "paymentOrderStatus", ignore = true)
    @Mapping(target = "generatedByUserName", ignore = true)
    ReportResponseDTO toResponseDTO(Report entity);
}
