package com.hyperplane.rodo.model;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.mongodb.core.mapping.Field;

@Value
@Builder(toBuilder = true)
public class Address {
    String street;
    String number;
    String neighborhood;
    String city;
    String state;
    @Field("zip_code")
    String zipCode;
    Double lat;
    Double lng;
}
