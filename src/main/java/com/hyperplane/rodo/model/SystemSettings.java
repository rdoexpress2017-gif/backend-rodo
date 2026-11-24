package com.hyperplane.rodo.model;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Value
@Builder(toBuilder = true)
@Document(collection = "system_settings")
public class SystemSettings {
    public static final String GLOBAL_CONFIG_ID = "GLOBAL_CONFIG";

    @Id
    String id;
    @Field("price_per_km")
    BigDecimal pricePerKm;
    @Field("base_rate")
    BigDecimal baseRate;
    @Field("default_motoboy_commission_percentage")
    Double defaultMotoboyCommissionPercentage;
    @Field("daily_job_executed_today")
    Boolean dailyJobExecutedToday;
    @Field("last_daily_job_execution_date")
    LocalDate lastDailyJobExecutionDate;
    @Field("daily_job_execution_time")
    String dailyJobExecutionTime;
    @Field("updated_by_user_id")
    String updatedByUserId;
    @Field("updated_at")
    Instant updatedAt;
}
