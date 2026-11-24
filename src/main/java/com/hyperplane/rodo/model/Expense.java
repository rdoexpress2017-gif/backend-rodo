package com.hyperplane.rodo.model;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder(toBuilder = true)
@Document(collection = "expenses")
public class Expense {
    @Id
    String id;

    @Indexed
    @Field("expense_date")
    Instant expenseDate;

    @Field("amount")
    BigDecimal amount;

    @Field("description")
    String description;

    @Field("created_at")
    Instant createdAt;

    @Field("updated_at")
    Instant updatedAt;
}
