package com.hyperplane.rodo.model;

import com.hyperplane.rodo.model.enums.BillingCycleType;
import com.hyperplane.rodo.model.enums.DayOfWeekEnum;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.mongodb.core.mapping.Field;

@Value
@Builder(toBuilder = true)
public class BillingCycle {
    BillingCycleType type;
    @Field("start_day_of_week")
    DayOfWeekEnum startDayOfWeek;
    @Field("end_day_of_week")
    DayOfWeekEnum endDayOfWeek;
}
