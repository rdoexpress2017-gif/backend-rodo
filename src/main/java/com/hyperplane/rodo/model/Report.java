package com.hyperplane.rodo.model;

import com.hyperplane.rodo.dto.CompanyReportSummaryDTO;
import com.hyperplane.rodo.model.enums.ReportType;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Value
@Builder(toBuilder = true)
@Document(collection = "reports")
public class Report {
    @Id
    String id;
    @Field("report_code")
    String reportCode;
    ReportType type;
    @Field("target_entity_id")
    String targetEntityId;
    @Field("order_ids")
    List<String> orderIds;
    @Field("period_start")
    Instant periodStart;
    @Field("period_end")
    Instant periodEnd;
    @Field("total_orders")
    Integer totalOrders;
    @Field("total_amount")
    BigDecimal totalAmount;
    @Field("total_distance_km")
    Double totalDistanceKm;
    @Field("company_summary")
    CompanyReportSummaryDTO companySummary;
    @Field("generated_by_user_id")
    String generatedByUserId;
    @Indexed(name = "idx_reports_created_at_ttl", expireAfterSeconds = 15552000)
    @Field("created_at")
    Instant createdAt;
    @Field("updated_at")
    Instant updatedAt;
}
