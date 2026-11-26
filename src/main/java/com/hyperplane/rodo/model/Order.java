package com.hyperplane.rodo.model;

import com.hyperplane.rodo.model.enums.OrderStatus;
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
@Document(collection = "orders")
public class Order {
    @Id
    String id;
    @Indexed(unique = true)
    @Field("order_number")
    Long orderNumber;
    @Indexed
    @Field("client_id")
    String clientId;
    @Indexed
    @Field("motoboy_id")
    String motoboyId;
    @Field("requester_name")
    String requesterName;
    @Field("created_by_user_id")
    String createdByUserId;
    @Field("origin_address")
    Address originAddress;
    @Field("destination_address")
    Address destinationAddress;
    @Field("route_distance_km")
    Double routeDistanceKm;
    BigDecimal amount;
    @Field("motoboy_commission_amount")
    BigDecimal motoboyCommissionAmount;
    OrderStatus status;
    @Indexed
    @Field("payment_order_id")
    String paymentOrderId;
    @Indexed(name = "idx_orders_created_at_ttl", expireAfterSeconds = 15552000)
    @Field("created_at")
    Instant createdAt;
    @Field("collected_at")
    Instant collectedAt;
    @Field("delivered_at")
    Instant deliveredAt;
    @Field("paid_at")
    Instant paidAt;
    @Field("updated_at")
    Instant updatedAt;
}
