package com.hyperplane.rodo.model;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Value
@Builder(toBuilder = true)
@Document(collection = "clients")
public class Client {
    @Id
    String id;
    String name;
    @Indexed
    String document;
    String email;
    String phone;
    @Field("base_address")
    Address baseAddress;
    @Field("billing_cycle")
    BillingCycle billingCycle;
    boolean active;
    @Field("created_at")
    Instant createdAt;
    @Field("updated_at")
    Instant updatedAt;
}
