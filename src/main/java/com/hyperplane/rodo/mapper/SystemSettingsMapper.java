package com.hyperplane.rodo.mapper;

import com.hyperplane.rodo.dto.SystemSettingsRequestDTO;
import com.hyperplane.rodo.dto.SystemSettingsResponseDTO;
import com.hyperplane.rodo.model.SystemSettings;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SystemSettingsMapper {
    @Mapping(target = "id", constant = "GLOBAL_CONFIG")
    @Mapping(target = "dailyJobExecutedToday", ignore = true)
    @Mapping(target = "lastDailyJobExecutionDate", ignore = true)
    @Mapping(target = "updatedByUserId", ignore = true)
    @Mapping(target = "updatedAt", expression = "java(java.time.Instant.now())")
    SystemSettings toEntity(SystemSettingsRequestDTO dto);

    SystemSettingsResponseDTO toResponseDTO(SystemSettings entity);
}
