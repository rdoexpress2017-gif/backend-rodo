package com.hyperplane.rodo.model;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Value
@Builder(toBuilder = true)
@Document(collection = "database_sequences")
public class DatabaseSequence {
    public static final String ORDERS_SEQUENCE_KEY = "orders_sequence";

    @Id
    String id;
    Long seq;
}
