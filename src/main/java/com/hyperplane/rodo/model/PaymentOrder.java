package com.hyperplane.rodo.model;

import com.hyperplane.rodo.model.enums.PaymentOrderStatus;
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
@Document(collection = "payment_orders")
public class PaymentOrder {
    @Id
    String id;
    @Indexed
    @Field("report_id")
    String reportId;
    @Indexed
    @Field("client_id")
    String clientId;
    @Field("order_ids")
    List<String> orderIds;
    @Field("total_amount")
    BigDecimal totalAmount;
    PaymentOrderStatus status;
    @Field("issue_date")
    Instant issueDate;
    @Field("settled_at")
    Instant settledAt;
    @Field("created_at")
    Instant createdAt;
    @Field("updated_at")
    Instant updatedAt;
}
