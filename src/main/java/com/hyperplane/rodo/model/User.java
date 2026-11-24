package com.hyperplane.rodo.model;

import com.hyperplane.rodo.model.enums.UserRole;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Value
@Builder(toBuilder = true)
@Document(collection = "users")
public class User {
    @Id
    String id;
    String name;
    @Indexed(unique = true)
    String email;
    @Field("password_hash")
    String passwordHash;
    String phone;
    UserRole role;
    boolean active;
    @Field("created_at")
    Instant createdAt;
    @Field("updated_at")
    Instant updatedAt;
}
