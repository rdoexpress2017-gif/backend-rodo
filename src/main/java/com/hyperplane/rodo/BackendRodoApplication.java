package com.hyperplane.rodo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendRodoApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendRodoApplication.class, args);
    }
}
